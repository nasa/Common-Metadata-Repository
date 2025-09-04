(ns cmr.indexer.data.elasticsearch
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [cmr.common.concepts :as cs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [info infof warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.connect :as es]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.search.es-index :as es-index]
   [cmr.elastic-utils.index-util :as esi]
   [cmr.indexer.config :as config]
   [cmr.indexer.indexer-util :as indexer-util]
   [cmr.indexer.data.concept-parser :as cp]
   [cmr.indexer.data.bulk :as cmr-bulk]
   [cmr.indexer.data.index-set :as idx-set]
   [cmr.indexer.data.index-set-elasticsearch :as index-set-es]
   [cmr.indexer.services.index-set-service :as index-set-svc]
   [cmr.transmit.config :as t-config]))

(def MAX_INT
  "The maximum `integer` type value supported by Elasticsearch, 2^31 - 1"
  2147483647)

(def MAX_TEXT_UTF8_ENCODING_BYTES
  "The maximum size in bytes that a string can be in Elasticsearch to be indexed as text"
  32766)

(def MAX_BULK_OPERATIONS_PER_REQUEST
  "The maximum number of operations to batch in a single request"
  100)

(defmulti get-elastic-version
  "Get the proper elastic document version for the concept based on type.
  Since this function is also used by bulk indexing, we figure out the concept type based on
  the concept id if the concept type field does not exist."
  (fn [concept]
    (or (:concept-type concept)
        (cs/concept-id->type (:concept-id concept)))))

(defmethod get-elastic-version :collection
  [concept]
  (apply max
         (:transaction-id concept)
         (concat (map :transaction-id (:tag-associations concept))
                 (map :transaction-id (:variable-associations concept))
                 (map :transaction-id (:service-associations concept))
                 (map :transaction-id (:tool-associations concept))
                 (map :transaction-id (:generic-associations concept)))))

(defmethod get-elastic-version :variable
  [concept]
  (apply max
         (:transaction-id concept)
         (map :transaction-id (:variable-associations concept))))

(defmethod get-elastic-version :service
  [concept]
  (apply max
         (:transaction-id concept)
         (map :transaction-id (:service-associations concept))))

(defmethod get-elastic-version :subscription
  [concept]
  (:transaction-id concept))

(defmethod get-elastic-version :tool
  [concept]
  (apply max
         (:transaction-id concept)
         (map :transaction-id (:tool-associations concept))))

(defmethod get-elastic-version :default
  [concept]
  (:revision-id concept))

(defn- get-elastic-id
  "Create the proper elastic document id for normal indexing or all-revisions indexing"
  [concept-id revision-id all-revisions-index?]
  (if (and
       (or (= :collection (cs/concept-id->type concept-id))
           (= :variable (cs/concept-id->type concept-id))
           (= :service (cs/concept-id->type concept-id))
           (= :tool (cs/concept-id->type concept-id))
           (= :subscription (cs/concept-id->type concept-id))
           (cs/generic-concept? (cs/concept-id->type concept-id)))
       all-revisions-index?)
    (str concept-id "," revision-id)
    concept-id))

(defmulti parsed-concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the
  given concept"
  (fn [_context concept _parsed-concept]
    (cs/concept-id->type (:concept-id concept))))

(defn index-set-requires-update?
  "Returns true if the existing index set does not match the expected index set and requires
  update. Takes either the context which will be used to request index sets or the existing
  and expected index sets.
  This does not compare the :concepts values when determining update status."
  [existing-index-set expected-index-set]
  (let [updated-value (update-in existing-index-set [:index-set] dissoc :concepts)
        result (not= updated-value expected-index-set)]
    result))

(defn cluster-requires-update?
  "Returns true if the existing index set does not match the expected index set and requires
  update. Takes either the context which will be used to request index sets or the existing
  and expected index sets."
  [context es-cluster-name]
  (let [existing-index-set (index-set-es/get-index-set context es-cluster-name idx-set/index-set-id)
        expected-index-set (cond
                             (= es-cluster-name es-config/elastic-name)
                             idx-set/non-gran-index-set

                             (= es-cluster-name es-config/gran-elastic-name)
                             idx-set/gran-index-set

                             :else (throw (Exception. (es-config/invalid-elastic-cluster-name-msg es-cluster-name))))]
    (index-set-requires-update? existing-index-set expected-index-set)))


(defn create-default-indexes
  "Create elastic indexes for each index name for both es clusters."
  [context]
  ;; create indexes for non-gran-cluster
  (let [;; setup for non-gran-cluster
        existing-non-gran-index-set (index-set-es/get-index-set context es-config/elastic-name idx-set/index-set-id)
        expected-non-gran-index-set (idx-set/non-gran-index-set)

        ;; setup for gran cluster
        existing-gran-index-set (index-set-es/get-index-set context es-config/gran-elastic-name idx-set/index-set-id)
        extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-gran-index-set)
        expected-gran-index-set (idx-set/gran-index-set extra-granule-indexes)]

    (cond
      ;; Check if exist and needs update
      (index-set-requires-update? existing-non-gran-index-set expected-non-gran-index-set)
      (do
        (warn "Non-gran index set does not match. You may want to update it. This is separate manual call you'll need to make.")
        (warn "Expected:" (pr-str expected-non-gran-index-set))
        (warn "Actual:" (pr-str existing-non-gran-index-set)))

      ;; Check if we need to create
      (nil? existing-non-gran-index-set)
      (do
        (index-set-svc/create-or-update-indexes-and-index-set context es-config/elastic-name expected-non-gran-index-set)
        (esi/create-index-alias (indexer-util/context->conn context es-config/elastic-name)
                                (idx-set/collections-index)
                                (idx-set/collections-index-alias)))
      :else
      (info "Non-gran index set exists and matches."))


    (cond
      ;; Check if we need to update
      (index-set-requires-update? existing-gran-index-set expected-gran-index-set)
      (do
        (warn "Gran index set does not match you may want to update it. This is separate manual call you'll need to make.")
        (warn "Expecting:" (pr-str expected-gran-index-set))
        (warn "Actual:" (pr-str existing-gran-index-set)))

      ;; Check if we need to create
      (nil? existing-gran-index-set)
      (do
        (warn "Gran index set does not exist so creating it.")
        (index-set-svc/create-or-update-indexes-and-index-set context es-config/gran-elastic-name expected-gran-index-set))

      :else
      (info "Gran index set exists and matches."))
    )
  )

(defn update-indexes
  "Updates the indexes to make sure they have the latest mappings"
  [context params]
  (let [existing-index-set (index-set-es/get-index-set context es-config/elastic-name idx-set/index-set-id)
        expected-index-set (idx-set/non-gran-index-set)]
    (if (or (= "true" (:force params))
            (index-set-requires-update? existing-index-set expected-index-set))
      (do
        (index-set-svc/validate-requested-index-set context es-config/elastic-name expected-index-set true)
        (index-set-svc/create-or-update-indexes-and-index-set context es-config/elastic-name expected-index-set)
        (info "Creating collection index alias.")
        (esi/create-index-alias (indexer-util/context->conn context es-config/elastic-name)
                                (idx-set/collections-index)
                                (idx-set/collections-index-alias)))
      (do
        (info "Ignoring update indexes request because non-gran index set is unchanged.")
        (info "Existing non-gran index set:" (pr-str existing-index-set))
        (info "New non-gran index set:" (pr-str expected-index-set)))))

  (let [existing-index-set (index-set-es/get-index-set context es-config/gran-elastic-name idx-set/index-set-id)
        extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-index-set)
        ;; We use the extra granule indexes from the existing configured index set when determining
        ;; the expected index set.
        expected-index-set (idx-set/gran-index-set extra-granule-indexes)]
    (if (or (= "true" (:force params))
            (index-set-requires-update? existing-index-set expected-index-set))
      (do
        (info "Updating the gran index set to " (pr-str expected-index-set))
        (index-set-svc/validate-requested-index-set context es-config/gran-elastic-name expected-index-set true)
        (index-set-svc/create-or-update-indexes-and-index-set context es-config/gran-elastic-name expected-index-set))
      (do
        (info "Ignoring update gran indexes request because gran index set is unchanged.")
        (info "Existing gran index set:" (pr-str existing-index-set))
        (info "New gran index set:" (pr-str expected-index-set))))))

(defn delete-granule-index
  "Delete an elastic index by name"
  [context index]
  (info (format "Deleting granule index %s from elastic" index))
  (try
    (esi/delete-index (indexer-util/context->conn context es-config/gran-elastic-name) index)
    (catch Throwable e
      (error e (str "Failed to delete granule index: "
                    (pr-str index))))))

(defn reset-es-store
  "Delete elasticsearch indexes and re-create them via index-set app. A nuclear option just for the development team."
  [context]
  (index-set-svc/reset context)
  (create-default-indexes context))

(defrecord ESstore
  [;; configuration of host, port and admin-token for elasticsearch
   config

   ;; The connection to elasticsearch
   conn

   ;; name of this es store
   ^String es-cluster-name
   ]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this _system]
    (let [conn (es/try-connect (:config this))
          this (assoc this :conn conn)]
      ;; this is creating the index set index with the list of indexes in Elastic... do we want them to be a different list in each cluster? Right now, the non-gran cluster has this index
      ;; unless we want a copy of this index set in each cluster with it being accurate to that specific index, or it only goes to the non-gran cluster...let's try the non-gran cluster only and see what it does
      (index-set-es/create-index this (config/idx-cfg-for-index-sets (:es-cluster-name this)))
      this))

  (stop [this _system]
    this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config es-cluster-name]
  (->ESstore config nil es-cluster-name))

(defn- try-elastic-operation
  "Attempt to perform the operation in Elasticsearch, handles exceptions.
  f is the operation function to Call
  conn is the elastisch connection
  es-index is the elasticsearch index name
  es-type is the elasticsearch mapping
  es-doc is the elasticsearch document to be passed on to elasticsearch
  elastic-id is the _id of the document in the index
  revision-id is the version of the document in elasticsearch"
  [f conn es-index es-type es-doc elastic-id revision-id]
  (let [options {:version revision-id :version_type "external_gte"}]
    (try
      (f conn es-index es-type elastic-id es-doc options)
      (catch clojure.lang.ExceptionInfo e
        (let [err-msg (get-in (ex-data e) [:body])
              msg (str "Call to Elasticsearch caught exception " err-msg)]
          (errors/internal-error! msg))))))

(defn- context->es-config
  "Returns the elastic config in the context"
  [context es-cluster-name]
  (get-in context [:system (es-config/es-cluster-name-str->keyword es-cluster-name) :config]))

(defn parse-non-tombstone-associations
  "Returns the parsed associations that are not tombstones"
  [context associations]
  (map #(cp/parse-concept context %) (filter #(not (:deleted %)) associations)))

(defn- non-tombstone-concept->bulk-elastic-doc
  "Takes a non-tombstoned concept map (a normal revision) and returns an elastic document suitable
   for bulk indexing."
  [context concept]
  (let [parsed-concept (cp/parse-concept context concept)]
    (parsed-concept->elastic-doc context concept parsed-concept)))

(defn- concept->bulk-elastic-docs
  "Converts a concept map into an elastic document suitable for bulk indexing."
  [context concept {:keys [all-revisions-index?] :as options}]
  (try
    (let [{:keys [concept-id revision-id]} concept
          elastic-version (get-elastic-version concept)
          concept (-> concept
                      (update :tag-associations #(parse-non-tombstone-associations context %))
                      (update :variable-associations #(parse-non-tombstone-associations context %))
                      (update :service-associations #(parse-non-tombstone-associations context %))
                      (update :tool-associations #(parse-non-tombstone-associations context %)))
          elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
          index-names (idx-set/get-concept-index-names
                       context concept-id revision-id options
                       concept)
          elastic-doc (if (:deleted concept)
                        ;; The concept is a tombstone
                        (parsed-concept->elastic-doc context concept concept)
                        ;; The concept is not a tombstone
                        (non-tombstone-concept->bulk-elastic-doc context concept))]

      ;; elastic-doc may be nil if the concept has a delete time in the past
      (when elastic-doc
        (let [elastic-doc (merge elastic-doc
                                 {:_id elastic-id
                                  :version elastic-version
                                  :version_type "external_gte"})]
          ;; Return one elastic document for each index we're writing to.
          (util/doall-recursive (mapv #(assoc elastic-doc :_index %) index-names)))))

    (catch Throwable e
      (error e (str "Skipping failed catalog item. Exception trying to convert concept to elastic doc:"
                    (pr-str concept))))))

(defn prepare-batch
  "Convert a batch of concepts into elastic docs for bulk indexing."
  [context concept-batch options]
  (doall
   (->> concept-batch
        (pmap #(concept->bulk-elastic-docs context % options))
        ;; Remove nils because some granules may fail with an exception and return nil.
        ;; or they may have been excluded because of delete time.
        (filter identity)
        flatten)))

(defn- handle-bulk-index-response
  "Logs any non-standard (not 409/404) errors found in the bulk index response."
  [response]
  ;; we don't care about version conflicts or deletes that aren't found
  (let [bad-items (filter (fn [item]
                            (let [status (if (:index item)
                                           (get-in item [:index :status])
                                           (get-in item [:delete :status]))]
                              (and (not (nil? status))
                                   (> status 399)
                                   (not= 409 status)
                                   (not= 404 status))))
                          (:items response))]
    (doseq [resp bad-items
            :let [resp-data (or (:index resp) (:delete resp))
                  {:keys [_id status error]} resp-data]]
      (log/error (format "[%s] failed bulk indexing with status [%d] and error [%s]" _id status error)))))

(defn bulk-index-autocomplete-suggestions
  "Save a batch of suggestion documents in Elasticsearch."
  [context docs]
  (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
    (let [bulk-operations (cmr-bulk/create-bulk-index-operations docs-batch)
          conn (indexer-util/context->conn context es-config/elastic-name)
          response (es-helper/bulk conn bulk-operations)]
      (handle-bulk-index-response response)))
  nil)

(defn bulk-index-documents
  "Save a batch of documents in Elasticsearch."
  ([context docs es-cluster-name]
   (bulk-index-documents context docs es-cluster-name nil))
  ([context docs es-cluster-name {:keys [all-revisions-index?]}]
   (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
     (let [bulk-operations (cmr-bulk/create-bulk-index-operations docs-batch all-revisions-index?)
           conn (indexer-util/context->conn context es-cluster-name)
           response (es-helper/bulk conn bulk-operations)]
       (handle-bulk-index-response response)))))

(defn get-es-cluster-conn
  [context es-index]
  ;; if es-index is granule type then get granule connection, else get the non-gran cluster connection
  (let [cluster-name (if
                       (and (not (= es-index (idx-set/collections-index)))
                            (or (string/starts-with? es-index idx-set/granule-index-name-prefix)
                                (= es-index idx-set/small-collections-index-name)
                                (= es-index idx-set/deleted-granule-index-name)))
                       (keyword es-config/gran-elastic-name)
                       (keyword es-config/elastic-name))]
    (indexer-util/context->conn context cluster-name)))

(defn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-indexes es-type es-doc concept-id revision-id elastic-version options]
  (doseq [es-index es-indexes]
    (let [conn (get-es-cluster-conn context es-index)
          {:keys [ignore-conflict? all-revisions-index?]} options
          elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
          result (try-elastic-operation
                  es-helper/put conn es-index es-type es-doc elastic-id elastic-version)]
      (when (:error result)
        (if (= 409 (:status result))
          (if ignore-conflict?
            (infof "Ignore conflict: %s" (str result))
            (errors/throw-service-error
             :conflict
             (format "Save to Elasticsearch failed %s" (str result))))
          (errors/internal-error!
           (format "Save to Elasticsearch failed %s" (str result))))))))

(defn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-type elastic-id]
  (let [es-cluster-name (es-index/get-es-cluster-name-from-concept-id elastic-id)]
    (es-helper/doc-get (indexer-util/context->conn context es-cluster-name) es-index es-type elastic-id)))

(defn delete-document
  "Delete the document from Elasticsearch, raise error if failed."
  ([context es-indexes es-type concept-id revision-id elastic-version]
   (delete-document context es-indexes es-type concept-id revision-id elastic-version nil))
  ([context es-indexes _es-type concept-id revision-id elastic-version options]
   (doseq [es-index es-indexes]
     ;; Cannot use elasticsearch for deletion as we require special headers on delete
     (let [es-cluster-name (es-index/get-es-cluster-name-from-concept-id concept-id)
           {:keys [admin-token]} (context->es-config context es-cluster-name)
           {:keys [uri http-opts]} (indexer-util/context->conn context es-cluster-name)
           {:keys [ignore-conflict? all-revisions-index?]} options
           elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
           delete-url (if elastic-version
                        (format "%s/%s/_doc/%s?version=%s&version_type=external_gte"
                                uri es-index elastic-id elastic-version)
                        (format "%s/%s/_doc/%s" uri es-index elastic-id))
           response (client/delete delete-url
                                   (merge http-opts
                                          {:headers {"Authorization" admin-token
                                                     "Confirm-delete-action" "true"
                                                     :client-id t-config/cmr-client-id}
                                           :throw-exceptions false}))
           status (:status response)]
       (when-not (some #{200 404} [status])
         (if (= 409 status)
           (if ignore-conflict?
             (infof "Ignore conflict: %s" (str response))
             (errors/throw-service-error :conflict (str "Delete from Elasticsearch failed " (str response))))
           (errors/internal-error! (str "Delete from Elasticsearch failed " (str response)))))))))

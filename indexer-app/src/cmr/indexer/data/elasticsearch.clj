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
   [cmr.elastic-utils.es-helper :as es-helper]
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

(defn- index-set->resharded-indexes
  "Given a map of concepts and their index mappings, return all indexes whose names
   end with `_\\d+_shards`, as a list of maps like:
   {:concept-type ..., :index-key ..., :index-name-without-id ..., :num-shards ...}"
  [concepts-map]
  (for [[concept-type index-map] (:concepts concepts-map)
        [index-key index-name] index-map
        :let [m (re-matches #".*_(\d+)_shards$" index-name)]
        :when m]
    {:concept-type concept-type
     :index-key (name index-key)
     :index-name-without-id (string/replace-first index-name #".*?_" "")
     :num-shards (parse-long (second m))}))

(defn reconcile-resharded-index
  "Update existing canonical index configs in expected-index-set
   based on resharded-indexes.

   For each resharded index:
   - If an index with name = index-name-without-id already exists, do nothing.
   - Otherwise, find the index in the given concept-type whose canonical key name
     matches the resharded index-name-without-id.
   - Update that index's :name to index-name-without-id and
     :settings :index :number_of_shards to num-shards.
   - Leave all other fields untouched."
  [expected-index-set resharded-indexes]
  (reduce
   (fn [updated-set {:keys [concept-type index-key index-name-without-id num-shards]}]
     (let [indexes-path [:index-set (keyword concept-type) :indexes]
           indexes (get-in updated-set indexes-path)
           ;; Check if the resharded index name already exists
           index-exists? (some #(= (:name %) index-name-without-id) indexes)]
       (if index-exists?
         ;; Skip if the index already exists
         updated-set
         ;; Otherwise update the matching canonical index
         (update-in updated-set indexes-path
                    (fn [idxs]
                      (mapv (fn [index]
                              (if (= (:name index) index-key)
                                (-> index
                                    (assoc :name index-name-without-id)
                                    (assoc-in [:settings :index :number_of_shards] num-shards))
                                index))
                            idxs))))))
   expected-index-set
   resharded-indexes))

(defn- compute-expected-index-set
  "Returns [existing-index-set expected-index-set] for a given context."
  [context]
  (let [existing-index-set (index-set-es/get-index-set context idx-set/index-set-id)
        existing-index-set (util/remove-nils-empty-maps-seqs existing-index-set)
        extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-index-set)
        expected-index-set (idx-set/index-set extra-granule-indexes)
        resharded-indexes (index-set->resharded-indexes (:index-set existing-index-set))
        expected-index-set (reconcile-resharded-index expected-index-set resharded-indexes)]
    [existing-index-set expected-index-set]))

(defn requires-update?
  "Returns true if the existing index set differs from the expected index set."
  ([context]
   (let [[existing expected] (compute-expected-index-set context)]
     (requires-update? existing expected)))
  ([existing expected]
   (not= (update-in existing [:index-set] dissoc :concepts)
         expected)))

(defn create-indexes
  "Create elastic index for each index name"
  [context]
  (let [[existing-index-set expected-index-set] (compute-expected-index-set context)]
    (cond
      (nil? existing-index-set)
      (do
        (info "Index set does not exist so creating it.")
        (index-set-svc/create-index-set context expected-index-set)
        (info "Creating collection index alias.")
        (esi/create-index-alias (indexer-util/context->conn context)
                                (idx-set/collections-index)
                                (idx-set/collections-index-alias)))

      ;; Compare them to see if they're the same
      (requires-update? existing-index-set expected-index-set)
      (do
        (warn "Index set does not match you may want to update it.")
        (warn "Expecting:" (pr-str expected-index-set))
        (warn "Actual:" (pr-str existing-index-set)))

      :else
      (info "Index set exists and matches."))))

(defn update-indexes
  "Updates the indexes to make sure they have the latest mappings"
  [context params]
  (let [[existing-index-set expected-index-set] (compute-expected-index-set context)]
    (if (or (= "true" (:force params))
            (requires-update? existing-index-set expected-index-set))
      (do
        (info "Updating the index set to " (pr-str expected-index-set))
        (index-set-svc/update-index-set context expected-index-set)
        (info "Creating colleciton index alias.")
        (esi/create-index-alias (indexer-util/context->conn context)
                                (idx-set/collections-index)
                                (idx-set/collections-index-alias)))
      (do
        (info "Ignoring upate indexes request because index set is unchanged.")
        (info "Existing index set:" (pr-str existing-index-set))
        (info "New index set:" (pr-str expected-index-set))))))

(defn delete-granule-index
  "Delete an elastic index by name"
  [context index]
  (info (format "Deleting granule index %s from elastic" index))
  (try
    (esi/delete-index (indexer-util/context->conn context) index)
    (catch Throwable e
      (error e (str "Failed to delete granule index: "
                    (pr-str index))))))

(defn reset-es-store
  "Delete elasticsearch indexes and re-create them via index-set app. A nuclear option just for the development team."
  [context]
  (index-set-svc/reset context)
  (create-indexes context))

(defrecord ESstore
           [;; configuration of host, port and admin-token for elasticsearch
            config

            ;; The connection to elasticsearch
            conn]

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this _system]
    (let [conn (es/try-connect (:config this))
          this (assoc this :conn conn)]
      (index-set-es/create-index this config/idx-cfg-for-index-sets)
      this))

  (stop [this _system]
    this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config]
  (->ESstore config nil))

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
  [context]
  (get-in context [:system :db :config]))

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
                      (update :tool-associations #(parse-non-tombstone-associations context %))
                      (update :generic-associations #(parse-non-tombstone-associations context %)))
          elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
          index-names (idx-set/get-concept-index-names
                       context concept-id revision-id options concept)
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
          conn (indexer-util/context->conn context)
          response (es-helper/bulk conn bulk-operations)]
      (handle-bulk-index-response response))))

(defn bulk-index-documents
  "Save a batch of documents in Elasticsearch."
  ([context docs]
   (bulk-index-documents context docs nil))
  ([context docs {:keys [all-revisions-index?]}]
   (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
     (let [bulk-operations (cmr-bulk/create-bulk-index-operations docs-batch all-revisions-index?)
           conn (indexer-util/context->conn context)
           response (es-helper/bulk conn bulk-operations)]
       (handle-bulk-index-response response)))))

(defn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-indexes es-type es-doc concept-id revision-id elastic-version options]
  (doseq [es-index es-indexes]
    (let [conn (indexer-util/context->conn context)
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
  (es-helper/doc-get (indexer-util/context->conn context) es-index es-type elastic-id))

(defn delete-document
  "Delete the document from Elasticsearch, raise error if failed."
  ([context es-indexes es-type concept-id revision-id elastic-version]
   (delete-document context es-indexes es-type concept-id revision-id elastic-version nil))
  ([context es-indexes _es-type concept-id revision-id elastic-version options]
   (doseq [es-index es-indexes]
     ;; Cannot use elastisch for deletion as we require special headers on delete
     (let [{:keys [admin-token]} (context->es-config context)
           {:keys [uri http-opts]} (indexer-util/context->conn context)
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

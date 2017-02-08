(ns cmr.indexer.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clojurewerkz.elastisch.rest.bulk :as bulk]
            [cmr.indexer.data.bulk :as cmr-bulk]
            [clj-http.client :as client]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as cs]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.elastic-utils.connect :as es]
            [cmr.elastic-utils.index-util :as esi]
            [cmr.transmit.index-set :as index-set]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.umm.umm-core :as umm]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common.time-keeper :as tk]
            [cheshire.core :as json]
            [cmr.indexer.data.concept-parser :as cp]))

(def MAX_BULK_OPERATIONS_PER_REQUEST
  "The maximum number of operations to batch in a single request"
  100)

(defn context->conn
  "Returns the elastisch connection in the context"
  [context]
  (get-in context [:system :db :conn]))

(defn get-elastic-version
  "Get the proper elastic document version for the concept based on type."
  [concept]
  (let [concept-type (cs/concept-id->type (:concept-id concept))]
    (if (= concept-type :collection)
      (apply max (:transaction-id concept) (map :transaction-id (:tag-associations concept)))
      (:revision-id concept))))

(defn- get-elastic-id
  "Create the proper elastic document id for normal indexing or all-revisions indexing"
  [concept-id revision-id all-revisions-index?]
  (if (and
        (= :collection (cs/concept-id->type concept-id))
        all-revisions-index?)
    (str concept-id "," revision-id)
    concept-id))

(defmulti parsed-concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the given concept"
  (fn [context concept parsed-concept]
    (cs/concept-id->type (:concept-id concept))))

(defn requires-update?
  "Returns true if the existing index set does not match the expected index set and requires
  update. Takes either the context which will be used to request index sets or the existing
  and expected index sets."
  ([context]
   (let [existing-index-set (index-set/get-index-set context idx-set/index-set-id)
         extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-index-set)
         expected-index-set (idx-set/index-set extra-granule-indexes)]
     (requires-update? existing-index-set expected-index-set)))
  ([existing-index-set expected-index-set]
   (not= (update-in existing-index-set [:index-set] dissoc :concepts)
         expected-index-set)))

(defn create-indexes
  "Create elastic index for each index name"
  [context]
  (let [existing-index-set (index-set/get-index-set context idx-set/index-set-id)
        extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-index-set)
        expected-index-set (idx-set/index-set extra-granule-indexes)]
    (cond
      (nil? existing-index-set)
      (do
        (info "Index set does not exist so creating it.")
        (idx-set/create context expected-index-set)
        (info "Creating collection index alias.")
        (esi/create-index-alias (context->conn context)
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
  [context]
  (let [existing-index-set (index-set/get-index-set context idx-set/index-set-id)
        extra-granule-indexes (idx-set/index-set->extra-granule-indexes existing-index-set)
        ;; We use the extra granule indexes from the existing configured index set when determining
        ;; the expected index set.
        expected-index-set (idx-set/index-set extra-granule-indexes)]

    (if (requires-update? existing-index-set expected-index-set)
      (do
        (info "Updating the index set to " (pr-str expected-index-set))
        (idx-set/update context expected-index-set)
        (info "Creating colleciton index alias.")
        (esi/create-index-alias (context->conn context)
                                (idx-set/collections-index)
                                (idx-set/collections-index-alias)))
      (do
        (info "Ignoring upate indexes request because index set is unchanged.")
        (info "Existing index set:" (pr-str existing-index-set))
        (info "New index set:" (pr-str expected-index-set))))))

(defn reset-es-store
  "Delete elasticsearch indexes and re-create them via index-set app. A nuclear option just for the development team."
  [context]
  (idx-set/reset context)
  (create-indexes context))

(defrecord ESstore
  [
   ;; configuration of host, port and admin-token for elasticsearch
   config

   ;; The connection to elasticsearch
   conn]


  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (let [context {:system system}
          conn (es/try-connect (:config this))]
      (assoc this :conn conn)))

  (stop [this system]
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
  revision-id is the version of the document in elasticsearch
  ttl time-to-live in milliseconds"
  [f conn es-index es-type es-doc elastic-id revision-id ttl]
  (let [options {:version revision-id :version_type "external_gte"}
        options (if ttl (merge options {:ttl ttl}) options)]
    (try
      (f conn es-index es-type elastic-id es-doc options)
      (catch clojure.lang.ExceptionInfo e
        (let [err-msg (get-in (ex-data e) [:body])
              msg (str "Call to Elasticsearch caught exception " err-msg)]
          (errors/internal-error! msg))))))

(defn- concept->type
  "Returns concept type for the given concept"
  [concept]
  (cs/concept-id->type (:concept-id concept)))

(defn- context->es-config
  "Returns the elastic config in the context"
  [context]
  (get-in context [:system :db :config]))

(defn- parse-non-tombstone-tag-associations
  "Returns the parsed tag associations that are not tombstones"
  [context tag-associations]
  (map #(cp/parse-concept context %) (filter #(not (:deleted %)) tag-associations)))

(defn- non-tombstone-concept->bulk-elastic-doc
  "Takes a non-tombstoned concept map (a normal revision) and returns an elastic document suitable
   with ttl fields for bulk indexing. "
  [context concept]
  (let [parsed-concept (cp/parse-concept context concept)
        delete-time (get-in parsed-concept
                            [:data-provider-timestamps :delete-time])
        now (tk/now)
        ttl (when delete-time
              (if (t/after? delete-time now)
                (t/in-millis (t/interval now delete-time))
                0))
        elastic-doc (parsed-concept->elastic-doc context concept parsed-concept)
        elastic-doc (if ttl
                      (assoc elastic-doc :_ttl ttl)
                      elastic-doc)]
    (if (or (nil? ttl)
            (> ttl 0))
      elastic-doc
      (info
       (str
        "Skipping expired concept ["
        (:concept-id concept)
        "] with delete-time ["
        (f/unparse (f/formatters :date-time) delete-time)
        "]")))))

(defn- concept->bulk-elastic-docs
  "Converts a concept map into an elastic document suitable for bulk indexing."
  [context concept {:keys [all-revisions-index?] :as options}]
  (try
    (let [{:keys [concept-id revision-id]} concept
          type (name (concept->type concept))
          elastic-version (get-elastic-version concept)
          concept (update concept :tag-associations #(parse-non-tombstone-tag-associations context %))
          elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
          index-names (idx-set/get-concept-index-names
                       context concept-id revision-id options
                       concept)
          elastic-doc (if (:deleted concept)
                        ;; The concept is a tombstone
                        (parsed-concept->elastic-doc context concept concept)
                        ;; The concept is not a tombstone
                        (non-tombstone-concept->bulk-elastic-doc context concept))
          version-type (if (:force-version? options)
                         ;; "the document will be indexed regardless of the version of the stored
                         ;; document or if there is no existing document. The given version will be
                         ;; used as the new version and will be stored with the new document."
                         "force"
                         ;; "only index the document if the given version is equal or higher than
                         ;; the version of the stored document."
                         "external_gte")]

      ;; elastic-doc may be nil if the concept has a delete time in the past
      (when elastic-doc
        (let [elastic-doc (merge elastic-doc
                                 {:_id elastic-id
                                  :_type type
                                  :_version elastic-version
                                  :_version_type version-type})]
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

(defn bulk-index-documents
  "Save a batch of documents in Elasticsearch."
  ([context docs]
   (bulk-index-documents context docs nil))
  ([context docs {:keys [all-revisions-index?]}]
   (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
     (let [bulk-operations (cmr-bulk/create-bulk-index-operations docs-batch all-revisions-index?)
           conn (context->conn context)
           response (bulk/bulk conn bulk-operations)
           ;; we don't care about version conflicts or deletes that aren't found
           bad-errors (some (fn [item]
                              (let [status (if (:index item)
                                             (get-in item [:index :status])
                                             (get-in item [:delete :status]))]
                                (and (> status 399)
                                     (not= 409 status)
                                     (not= 404 status))))
                            (:items response))]
       (when bad-errors
         (errors/internal-error! (format "Bulk indexing failed with response %s" response)))))))

(defn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-indexes es-type es-doc concept-id revision-id elastic-version options]
  (doseq [es-index es-indexes]
    (let [conn (context->conn context)
          {:keys [ttl ignore-conflict? all-revisions-index?]} options
          elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
          result (try-elastic-operation
                  doc/put conn es-index es-type es-doc elastic-id elastic-version ttl)]
      (if (:error result)
        (if (= 409 (:status result))
          (if ignore-conflict?
            (info (str "Ignore conflict: " (str result)))
            (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result))))
          (errors/internal-error! (str "Save to Elasticsearch failed " (str result))))))))

(defn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-type elastic-id]
  (doc/get (context->conn context) es-index es-type elastic-id))

(defn delete-document
  "Delete the document from Elasticsearch, raise error if failed."
  ([context es-indexes es-type concept-id revision-id elastic-version]
   (delete-document context es-indexes es-type concept-id revision-id elastic-version nil))
  ([context es-indexes es-type concept-id revision-id elastic-version options]
   (doseq [es-index es-indexes]
     ;; Cannot use elastisch for deletion as we require special headers on delete
     (let [{:keys [admin-token]} (context->es-config context)
           {:keys [uri http-opts]} (context->conn context)
           {:keys [ignore-conflict? all-revisions-index?]} options
           elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
           delete-url (if elastic-version
                        (format "%s/%s/%s/%s?version=%s&version_type=external_gte" uri es-index es-type
                                elastic-id elastic-version)
                        (format "%s/%s/%s/%s" uri es-index es-type elastic-id))
           response (client/delete delete-url
                                   (merge http-opts
                                          {:headers {"Authorization" admin-token
                                                     "Confirm-delete-action" "true"}
                                           :throw-exceptions false}))
           status (:status response)]
       (if-not (some #{200 404} [status])
         (if (= 409 status)
           (if ignore-conflict?
             (info (str "Ignore conflict: " (str response)))
             (errors/throw-service-error :conflict (str "Delete from Elasticsearch failed " (str response))))
           (errors/internal-error! (str "Delete from Elasticsearch failed " (str response)))))))))

(defn delete-by-query
  "Delete document that match the given query"
  [context es-index es-type query]
  (let [{:keys [admin-token]} (context->es-config context)
        {:keys [uri http-opts]} (context->conn context)
        delete-url (format "%s/%s/%s/_query" uri es-index es-type)]
    (client/delete delete-url
                   (merge http-opts
                          {:headers {"Authorization" admin-token
                                     "Confirm-delete-action" "true"}
                           :body (json/generate-string {:query query})}))))

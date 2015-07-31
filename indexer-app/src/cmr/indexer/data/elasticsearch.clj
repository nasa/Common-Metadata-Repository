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
            [cmr.elastic-utils.connect :as es]
            [cmr.transmit.index-set :as index-set]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.system-trace.core :refer [deftracefn]]
            [cmr.umm.core :as umm]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common.time-keeper :as tk]
            [cheshire.core :as json]))

(def MAX_BULK_OPERATIONS_PER_REQUEST
  "The maximum number of operations to batch in a single request"
  100)

(defn- get-elastic-id
  "Create the proper elastic document id for normal indexing or all-revisions indexing"
  [concept-id revision-id all-revisions-index?]
  (if (and
        (= :collection (cs/concept-id->type concept-id))
        all-revisions-index?)
    (str concept-id "," revision-id)
    concept-id))

(def supported-formats
  "Defines the set of supported concept forms, new forms shold be added once it is supported."
  #{mt/echo10 mt/dif mt/iso-smap mt/iso})

(defmulti concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the given concept"
  (fn [context concept umm-concept]
    (cs/concept-id->type (:concept-id concept))))

(defn requires-update?
  "Returns true if the existing index set does not match the configured index set and requires
  update. Takes either the context which will be used to request index sets or the existing
  and updated index sets."
  ([context]
   (let [new-index-set (idx-set/index-set context)
         index-set-id (get-in new-index-set [:index-set :id])
         existing-index-set (index-set/get-index-set context index-set-id)]
     (requires-update? existing-index-set new-index-set)))
  ([existing-index-set new-index-set]
   (not= (update-in existing-index-set [:index-set] dissoc :concepts)
         new-index-set)))

(defn create-indexes
  "Create elastic index for each index name"
  [context]
  (let [new-index-set (idx-set/index-set context)
        index-set-id (get-in new-index-set [:index-set :id])
        existing-index-set (index-set/get-index-set context index-set-id)]
    (cond
      (nil? existing-index-set)
      (do
        (info "Index set does not exist so creating it.")
        (idx-set/create context new-index-set))

      ;; Compare them to see if they're the same
      (requires-update? existing-index-set new-index-set)
      (do
        (warn "Index set does not match you may want to update it.")
        (warn "Expecting:" (pr-str new-index-set))
        (warn "Actual:" (pr-str existing-index-set)))

      :else
      (info "Index set exists and matches."))))

(defn update-indexes
  "Updates the indexes to make sure they have the latest mappings"
  [context]

  (let [new-index-set (idx-set/index-set context)
        index-set-id (get-in new-index-set [:index-set :id])
        existing-index-set (index-set/get-index-set context index-set-id)]

    (when-not (requires-update? existing-index-set new-index-set)
      (info "Existing index set:" (pr-str existing-index-set))
      (info "New index set:" (pr-str new-index-set))
      (errors/throw-service-error :bad-request "It appears the existing index set and the new index set are the same."))

    (info "Updating the index set to " (pr-str new-index-set))
    (idx-set/update context new-index-set)))

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
   conn
   ]

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
        (let [err-msg (get-in (ex-data e) [:object :body])
              msg (str "Call to Elasticsearch caught exception " err-msg)]
          (errors/internal-error! msg))))))

(defn- concept->type
  "Returns concept type for the given concept"
  [concept]
  (cs/concept-id->type (:concept-id concept)))

(defn context->conn
  "Returns the elastisch connection in the context"
  [context]
  (get-in context [:system :db :conn]))

(defn- context->es-config
  "Returns the elastic config in the context"
  [context]
  (get-in context [:system :db :config]))

(defn prepare-batch
  "Convert a batch of concepts into elastic docs for bulk indexing."
  [context concept-batch all-revisions-index?]
  (let [parseable-batch (filterv #(supported-formats (:format %)) concept-batch)
        num-skipped (- (count concept-batch) (count parseable-batch))]
    (when (> num-skipped 0)
      (debug "Skipping" num-skipped "concepts that are not in a supported format."))
    (doall
      ;; Remove nils because some granules may fail with an exception and return nil.
      (filter identity
              (pmap (fn [concept]
                      (try
                        (let [{:keys [concept-id revision-id]} concept
                              type (name (concept->type concept))
                              elastic-id (get-elastic-id concept-id revision-id all-revisions-index?)
                              index-name (idx-set/get-concept-index-name
                                           context concept-id revision-id all-revisions-index?
                                           concept)]
                          (if (:deleted concept)
                            (merge concept {:_id elastic-id
                                            :_index index-name
                                            :_type type
                                            :_version revision-id
                                            :_version_type "external_gte"})
                            (let [umm-concept (umm/parse-concept concept)
                                  delete-time (get-in umm-concept
                                                      [:data-provider-timestamps :delete-time])
                                  now (tk/now)
                                  ttl (when delete-time
                                        (if (t/after? delete-time now)
                                          (t/in-millis (t/interval now delete-time))
                                          0))
                                  elastic-doc (concept->elastic-doc context concept umm-concept)
                                  elastic-doc (if ttl
                                                (assoc elastic-doc :_ttl ttl)
                                                elastic-doc)]
                              (if (or (nil? ttl)
                                      (> ttl 0))
                                (merge elastic-doc {:_id elastic-id
                                                    :_index index-name
                                                    :_type type
                                                    :_version revision-id
                                                    :_version_type "external_gte"})
                                (info
                                  (str
                                    "Skipping expired concept ["
                                    concept-id
                                    "] with delete-time ["
                                    (f/unparse (f/formatters :date-time) delete-time)
                                    "]"))))))
                        (catch Throwable e
                          (error e (str "Skipping failed catalog item. Exception trying to convert concept to elastic doc:"
                                        (pr-str concept))))))
                    parseable-batch)))))

(deftracefn bulk-index
  "Save a batch of documents in Elasticsearch."
  [context docs]
  (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
    (let [bulk-operations (cmr-bulk/bulk-index docs-batch)
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
        (errors/internal-error! (format "Bulk indexing failed with response %s" response))))))

(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-index es-type es-doc elastic-id revision-id ttl ignore-conflict]
  (let [conn (context->conn context)
        result (try-elastic-operation doc/put conn es-index es-type es-doc elastic-id revision-id ttl)]
    (if (:error result)
      (if (= 409 (:status result))
        (if ignore-conflict
          (info (str "Ignore conflict: " (str result)))
          (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result))))
        (errors/internal-error! (str "Save to Elasticsearch failed " (str result)))))))

(deftracefn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-type id]
  (doc/get (context->conn context) es-index es-type id))

(deftracefn delete-document
  "Delete the document from Elasticsearch, raise error if failed."
  [context es-index es-type id revision-id ignore-conflict]
  ;; Cannot use elastisch for deletion as we require special headers on delete
  (let [{:keys [admin-token]} (context->es-config context)
        {:keys [uri http-opts]} (context->conn context)
        delete-url (format "%s/%s/%s/%s?version=%s&version_type=external_gte" uri es-index es-type id revision-id)
        response (client/delete delete-url
                                (merge http-opts
                                       {:headers {"Authorization" admin-token
                                                  "Confirm-delete-action" "true"}
                                        :throw-exceptions false}))
        status (:status response)]
    (if-not (some #{200 404} [status])
      (if (= 409 status)
        (if ignore-conflict
          (info (str "Ignore conflict: " (str response)))
          (errors/throw-service-error :conflict (str "Delete from Elasticsearch failed " (str response))))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str response)))))))

(deftracefn delete-by-query
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

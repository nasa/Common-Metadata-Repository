(ns cmr.indexer.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-http.client :as client]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.index-set :as index-set]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.system-trace.core :refer [deftracefn]]
            [cheshire.core :as json]))


(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect (str "http://" host ":" port))))

(defn create-indexes
  "Create elastic index for each index name"
  [context]
  (let [index-set (idx-set/index-set context)
        index-set-id (get-in index-set [:index-set :id])]
    (when-not (index-set/get-index-set index-set-id)
      (idx-set/create index-set))))

(defn reset-es-store
  "Delete elasticsearch indexes and re-create them via index-set app. A nuclear option just for the development team."
  [context]
  (idx-set/reset)
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
          elastic-config (idx-set/get-elastic-config context)
          conn (connect-with-config elastic-config)
          this (assoc this :conn conn)]
      (create-indexes context)
      (assoc this
             :config elastic-config
             :conn conn)))

  (stop [this system]
        this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  []
  (map->ESstore {}))

(defn- try-elastic-operation
  "Attempt to perform the operation in Elasticsearch, handles exceptions.
  f is the operation function to Call
  conn is the elastisch connection
  es-index is the elasticsearch index name
  es-type is the elasticsearch mapping
  es-doc is the elasticsearch document to be passed on to elasticsearch
  revision-id is the version of the document in elasticsearch
  ttl time-to-live in milliseconds"
  [f conn es-index es-type es-doc revision-id ttl]
  (let [options {:version revision-id :version_type "external"}
        options (if ttl (merge options {:ttl ttl}) options)]
    (try
      (f conn es-index es-type (:concept-id es-doc) es-doc options)
      (catch clojure.lang.ExceptionInfo e
        (let [err-msg (get-in (ex-data e) [:object :body])
              msg (str "Call to Elasticsearch caught exception " err-msg)]
          (errors/internal-error! msg))))))

(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-index es-type es-doc revision-id ttl ignore-conflict]
  (let [conn (get-in context [:system :db :conn])
        result (try-elastic-operation doc/put conn es-index es-type es-doc revision-id ttl)]
    (if (:error result)
      (if (= 409 (:status result))
        (if ignore-conflict
          (info (str "Ignore conflict: " (str result)))
          (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result))))
        (errors/internal-error! (str "Save to Elasticsearch failed " (str result)))))))

(deftracefn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-type id]
  (doc/get (get-in context [:system :db :conn]) es-index es-type id))

(deftracefn delete-document
  "Delete the document from Elasticsearch, raise error if failed."
  [context es-config es-index es-type id revision-id ignore-conflict]
  ;; Cannot use elastisch for deletion as we require special headers on delete
  #_(let [result (try-elastic-operation doc/delete es-index es-type id)]
      (if (not (:ok result))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str result)))))
  (let [{:keys [host port admin-token]} es-config
        delete-url (format "http://%s:%s/%s/%s/%s?version=%s&version_type=external_gte" host port es-index es-type id revision-id)
        response (client/delete delete-url
                                {:headers {"Authorization" admin-token
                                           "Confirm-delete-action" "true"}
                                 :throw-exceptions false})
        status (:status response)]
    (if-not (some #{200 404} [status])
      (if (= 409 status)
        (if ignore-conflict
          (info (str "Ignore conflict: " (str response)))
          (errors/throw-service-error :conflict (str "Delete from Elasticsearch failed " (str response))))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str response)))))))

(deftracefn delete-by-query
  "Delete document that match the given query"
  [context es-config es-index es-type query]
  (let [{:keys [host port admin-token]} es-config
        delete-url (format "http://%s:%s/%s/%s/_query" host port es-index es-type)]
    (client/delete delete-url
                   {:headers {"Authorization" admin-token
                              "Confirm-delete-action" "true"}
                    :body (json/generate-string {:query query})})))

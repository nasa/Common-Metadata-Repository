(ns cmr.indexer.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-http.client :as client]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.elasticsearch-properties :as es-prop]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect! (str "http://" host ":" port))))

(defn- create-indexes
  "Create elastic index for each index name"
  []
  (doseq [{:keys [index-name setting mapping]} es-prop/indexes]
    (when-not (esi/exists? index-name)
      (let [response (esi/create index-name :settings setting :mappings mapping)]
        (info "index creation attempt result:" response)))))

(defn- delete-indexes
  "Delete configured elastic indexes"
  [{:keys [admin-token] :as es-config}]
  (doseq [{:keys [index-name]} es-prop/indexes]
    (when (esi/exists? index-name)
      (let [response (client/delete (esr/index-url index-name)
                                    {:headers {"Authorization" admin-token
                                               "Confirm-delete-action" "true"}
                                     :throw-exceptions false})
            status (:status response)]
        (if-not (some #{200 202 204} [status])
          (errors/internal-error! (str "Index name: " index-name " delete elasticsearch index operation failed " response)))))))

(defn reset-es-store
  "Delete elasticsearch indexes and re-create them. A nuclear option just for the development team."
  [context es-config]
  (delete-indexes es-config)
  (create-indexes))

(defrecord ESstore
  [
   ;; configuration of host, port and admin-token for elasticsearch
   config
   ]

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  lifecycle/Lifecycle

  (start
    [this system]
    (connect-with-config (:config this))
    (create-indexes)
    this)

  (stop [this system]
        this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config]
  (map->ESstore {:config config}))

(defn- try-elastic-operation
  "Attempt to perform the operation in Elasticsearch, handles exceptions.
  f is the operation function to Call
  es-index is the elasticsearch index name
  es-mapping is the elasticsearch mapping
  es-doc is the elasticsearch document to be passed on to elasticsearch
  revision-id is the version of the document in elasticsearch"
  [f es-index es-mapping es-doc revision-id]
  (try
    (f es-index es-mapping (:concept-id es-doc) es-doc :version revision-id :version_type "external")
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:object :body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (errors/internal-error! msg)))))

(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error if failed."
  [context es-index es-mapping es-doc revision-id ignore-conflict]
  (let [result (try-elastic-operation doc/put es-index es-mapping es-doc revision-id)]
    (if (:error result)
      (if (= 409 (:status result))
        (if ignore-conflict
          (info (str "Ignore conflict: " (str result)))
          (errors/throw-service-error :conflict (str "Save to Elasticsearch failed " (str result))))
        (errors/internal-error! (str "Save to Elasticsearch failed " (str result)))))))

(deftracefn get-document
  "Get the document from Elasticsearch, raise error if failed."
  [context es-index es-mapping id]
  (doc/get es-index es-mapping id))

(deftracefn delete-document-in-elastic
  "Delete the document from Elasticsearch, raise error if failed."
  [context es-config es-index es-mapping id revision-id ignore-conflict]
  ;; Cannot use elastisch for deletion as we require special headers on delete
  #_(let [result (try-elastic-operation doc/delete es-index es-mapping id)]
      (if (not (:ok result))
        (errors/internal-error! (str "Delete from Elasticsearch failed " (str result)))))
  (let [{:keys [host port admin-token]} es-config
        delete-url (format "http://%s:%s/%s/%s/%s?version=%s&version_type=external" host port es-index es-mapping id revision-id)
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






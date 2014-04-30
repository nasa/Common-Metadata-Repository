(ns cmr.index-set.data.elasticsearch
  (:require [cmr.common.lifecycle :as lifecycle]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clj-http.client :as client]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.index-set.services.messages :as m]
            [cheshire.core :as cheshire]
            [cmr.index-set.config.elasticsearch-config :as es-config]
            [cmr.system-trace.core :refer [deftracefn]]))

(defn- connect-with-config
  "Connects to ES with the given config"
  [config]
  (let [{:keys [host port]} config]
    (info (format "Connecting to single ES on %s %d" host port))
    (esr/connect! (str "http://" host ":" port))))

(defn create-index
  "Create elastic index"
  [idx-w-config]
  (let [{:keys [index-name settings mapping]} idx-w-config]
    (when-not (esi/exists? index-name)
      (try
        (esi/create index-name :settings settings :mappings mapping)
        (catch Exception e
          (let [body (cheshire/decode (get-in (ex-data e) [:object :body]) true)
                error (:error body)]
            (info (format "error creating %s elastic index, elastic reported error: %s" index-name error)))
          (throw e))))))

(defn index-set-exists?
  "Check index-set existence in elastic."
  [index-name idx-mapping-type index-set-id]
  (when (esi/exists? index-name)
    ;; result will be nil if doc doeesn't exist
    (doc/get index-name idx-mapping-type (str index-set-id) "fields" "index-set-id,index-set-name,index-set-request")))

(defn get-index-set
  "Fetch index-set associated with an id."
  [index-name idx-mapping-type index-set-id]
  (when (esi/exists? index-name)
    (let [result (doc/get index-name idx-mapping-type (str index-set-id) "fields" "index-set-id,index-set-name,index-set-request")
          index-set-json-str (get-in result [:fields :index-set-request])]
      (when-not result
        (errors/throw-service-error :not-found
                                    (m/index-set-not-found-msg index-set-id)))
      (cheshire.core/decode (first index-set-json-str) true))))

(defn get-index-set-ids
  "Fetch ids of all index-sets in elastic."
  [index-name idx-mapping-type]
  (when (esi/exists? index-name)
    (let [result (doc/search index-name idx-mapping-type "fields" "index-set-id")]
      (map #(-> % :fields :index-set-id) (get-in result [:hits :hits])))))


(defn get-index-sets
  "Fetch all index-sets in elastic."
  [index-name idx-mapping-type]
  (when (esi/exists? index-name)
    (let [result (doc/search index-name idx-mapping-type "fields" "index-set-request")]
      (map (comp #(cheshire/decode (first % ) true) :index-set-request :fields) (get-in result [:hits :hits])))))

(defn delete-index
  "Delete given elastic index"
  [index-name es-cfg]
  (when (esi/exists? index-name)
    (let [admin-token (:admin-token es-cfg)
          response (client/delete (esr/index-url index-name)
                                  {:headers {"Authorization" admin-token
                                             "Confirm-delete-action" "true"}
                                   :throw-exceptions false})
          status (:status response)]
      (if-not (some #{200 202 204} [status])
        (errors/internal-error! (m/index-delete-failure-msg response))))))

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
    (create-index es-config/idx-cfg-for-index-sets)
    this)

  (stop [this system]
        this))

(defn create-elasticsearch-store
  "Creates the Elasticsearch store."
  [config]
  (map->ESstore {:config config}))

(deftracefn save-document-in-elastic
  "Save the document in Elasticsearch, raise error on failure."
  [context es-index es-mapping-type doc-id es-doc]
  (try
    (let [result (doc/put es-index es-mapping-type doc-id es-doc)
          {:keys [error status]} result]
      (when (:error result)
        ;; service layer to rollback index-set create  progress on error
        ;; to result in 503 if replicas setting value of 'indext-sets' is set to > 0 when running on a single node
        (throw (Exception. (format "Save to Elasticsearch failed. Reported status: %s and error: %s " status error)))))
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:object :body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (throw (Exception. msg))))))

(deftracefn delete-document
  "Delete the document from elastic, raise error on failure."
  [context es-cfg index-name mapping-type id]
  (let [{:keys [host port admin-token]} es-cfg
        delete-doc-url (format "http://%s:%s/%s/%s/%s" host port index-name mapping-type id)
        result (client/delete delete-doc-url
                              {:headers {"Authorization" admin-token
                                         "Confirm-delete-action" "true"}
                               :throw-exceptions false})
        status (:status result)]
    (when-not (= status 200)
      (errors/internal-error! (m/index-set-doc-delete-msg result)))))

(comment
  (doc/get "index-sets" "set" "1" "fields" "index-set-id,index-set-name,index-set-request")
  )

(ns cmr.indexer.data.index-set-elasticsearch
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojurewerkz.elastisch.rest :as esr]
   [cmr.common.log :as log :refer [info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.config :as es-config]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.es-index-helper :as esi-helper]
   [cmr.elastic-utils.search.es-index :as es-index]
   [cmr.indexer.config :as config]
   [cmr.indexer.services.messages :as m]
   [cmr.indexer.indexer-util :as idx-util]
   [cmr.transmit.config :as t-config]))

(defn- decode-field
  "Attempt to decode a field using gzip, b64. Return the original field json decoded
  if there are any exceptions. This function is here to deal with any legacy fields that are not
  gzipped."
  [value]
  (try
    (-> value util/gzip-base64->string (cheshire/decode true))
    (catch Exception e
      (warn (.getMessage e))
      (cheshire/decode value true))))

(defn create-index
  "Create elastic index"
  [{:keys [conn]} idx-w-config]
  (let [{:keys [index-name settings mapping]} idx-w-config]
    (when-not (esi-helper/exists? conn index-name)
      (try
        (info "Now creating Elastic Index:" index-name)
        (esi-helper/create conn index-name {:settings settings :mappings mapping})
        (catch clojure.lang.ExceptionInfo e
          (let [body (cheshire/decode (get-in (ex-data e) [:body]) true)
                error-message (:error body)]
            (error (format "error creating %s elastic index, elastic reported error: %s"
                          index-name error-message))
            (throw e)))))))

(defn create-or-update-index
  "Update elastic index"
  [{:keys [conn]} idx-w-config]
  (let [{:keys [index-name settings mapping]} idx-w-config]
    (try
      (if (esi-helper/exists? conn index-name)
        ;; The index exists. Update the mappings.
        (doseq [[type-name] mapping]
          (let [response (esi-helper/update-mapping
                           conn index-name (name type-name) {:mapping mapping})]
            (when-not (= {:acknowledged true} response)
              (errors/internal-error! (str "Unexpected response when updating elastic mappings: "
                                           (pr-str response))))))
        ;; The index does not exist. Create it.
        (do
          (info "Index" index-name "does not exist so it will be created")
          (esi-helper/create conn index-name {:settings settings :mappings mapping})))
      (catch clojure.lang.ExceptionInfo e
        (let [body (cheshire/decode (get-in (ex-data e) [:body]) true)
              error-message (:error body)]
          (error (format "error updating %s elastic index, elastic reported error: %s"
                        index-name error-message))
          (throw e))))))

(defn get-index-set-if-exists
  "Check index-set existence in specific elastic cluster."
  [{:keys [conn]} index-name idx-mapping-type index-set-id]
  (when (esi-helper/exists? conn index-name)
    ;; result will be nil if doc doesn't exist
    (es-helper/doc-get
     conn
     index-name
     idx-mapping-type
     (str index-set-id)
     {"_source" "index-set-id,index-set-name,index-set-request"})))

(defn get-index-set
  "Fetch index-set associated with an id and a specific elastic cluster."
  [context es-cluster-name index-set-id]
  (let [es-cluster-name-keyword (es-config/es-cluster-name-str->keyword es-cluster-name)
        {:keys [index-name mapping]} (config/idx-cfg-for-index-sets es-cluster-name)
        _ (println ">>>>> INSIDE get-index-set with es-cluster-name = " es-cluster-name ", index-name = " index-name " and mapping = " mapping)
        idx-mapping-type (first (keys mapping))]
    (when-let [result (get-index-set-if-exists
                       (get-in context [:system es-cluster-name-keyword]) index-name idx-mapping-type index-set-id)]
      (-> result
          (get-in [:_source :index-set-request])
          decode-field))))

(defn get-index-set-ids
  "Fetch ids of all index-sets in specific elastic cluster."
  [{:keys [conn]} index-name idx-mapping-type]
  (when (esi-helper/exists? conn index-name)
    (let [result (es-helper/search
                  conn index-name idx-mapping-type {"_source" "index-set-id"})]
      (map #(-> % :_source :index-set-id) (get-in result [:hits :hits])))))

(defn get-index-sets
  "Fetch all index-sets in specific elastic cluster."
  [{:keys [conn]} index-name idx-mapping-type]
  (when (esi-helper/exists? conn index-name)
    (let [result (es-helper/search
                  conn index-name idx-mapping-type {"_source" "index-set-request"})]
      (map #(decode-field (get-in % [:_source :index-set-request]))
           (get-in result [:hits :hits])))))

(defn delete-index
  "Delete given elastic index in specific elastic cluster"
  [{:keys [conn config]} index-name]
  (when (esi-helper/exists? conn index-name)
    (let [admin-token (:admin-token config)
          response (client/delete (esr/index-url conn index-name)
                                  {:headers {"Authorization" admin-token
                                             "Confirm-delete-action" "true"
                                             :client-id t-config/cmr-client-id}
                                   :throw-exceptions false})
          status (:status response)]
      (when-not (some #{200 202 204} [status])
        (errors/internal-error! (m/index-delete-failure-msg response))))))

(defn save-document-in-elastic
  "Save the document in Elasticsearch in specific elastic cluster, raise error on failure."
  [context es-index es-mapping-type doc-id es-doc es-cluster-name]
  (try
    (println "INSIDE save-document-in-elastic with es-index = " es-index " and es-mapping-type = " es-mapping-type " and doc-id = " doc-id " and es-doc = " es-doc)
    (let [conn (get-in context [:system (es-config/es-cluster-name-str->keyword es-cluster-name) :conn])
          _ (println "conn = " conn)
          result (es-helper/put conn es-index es-mapping-type doc-id es-doc)
          _ (esi-helper/refresh conn es-index)
          _ (println "result of es put = " result)
          {:keys [error status]} result
          saved-index-set (get-index-set context es-config/gran-elastic-name doc-id)
          _ (println "getting back index-set we just saved = " saved-index-set)]
      (when (:error result)
        ;; service layer to rollback index-set create  progress on error
        ;; to result in 503 if replicas setting value of 'index-sets' is set to > 0 when running on a single node
        (throw (Exception. (format "Save to Elasticsearch failed. Reported status: %s and error: %s " status error)))))
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (throw (Exception. msg))))))

(defn delete-document
  "Delete the document from specific elastic cluster, raise error on failure."
  [context index-name _mapping-type id es-cluster-name]
  (let [{:keys [host port admin-token]} (get-in context [:system (keyword es-cluster-name) :config])
        delete-doc-url (format "http://%s:%s/%s/_doc/%s?refresh=true" host port index-name id)
        result (client/delete delete-doc-url
                              {:headers {"Authorization" admin-token
                                         "Confirm-delete-action" "true"}
                               :throw-exceptions false})
        status (:status result)]
    (when-not (= status 200)
      (errors/internal-error! (m/index-set-doc-delete-msg result)))))

(comment
  (es-helper/doc-get "index-sets" "set" "1" {"fields" "index-set-id,index-set-name,index-set-request"}))

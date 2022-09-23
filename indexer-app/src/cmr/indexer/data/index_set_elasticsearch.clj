(ns cmr.indexer.data.index-set-elasticsearch
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojurewerkz.elastisch.rest :as esr]
   [cmr.common.log :as log :refer [info warn error]]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.es-index-helper :as esi-helper]
   [cmr.indexer.config :as config]
   [cmr.indexer.services.messages :as m]))

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

(defn update-index
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

(defn index-set-exists?
  "Check index-set existence in elastic."
  [{:keys [conn]} index-name idx-mapping-type index-set-id]
  (when (esi-helper/exists? conn index-name)
    ;; result will be nil if doc doeesn't exist
    (es-helper/doc-get
     conn
     index-name
     idx-mapping-type
     (str index-set-id)
     {"_source" "index-set-id,index-set-name,index-set-request"})))

(defn get-index-set
  "Fetch index-set associated with an id."
  [context index-set-id]
  (let [conn (get-in context [:system :db :conn])
        {:keys [index-name mapping]} config/idx-cfg-for-index-sets
        idx-mapping-type (first (keys mapping))]
    (when-let [result (index-set-exists?
                       (get-in context [:system :db]) index-name idx-mapping-type index-set-id)]
      (-> result
          (get-in [:_source :index-set-request])
          decode-field))))

(defn get-index-set-ids
  "Fetch ids of all index-sets in elastic."
  [{:keys [conn]} index-name idx-mapping-type]
  (when (esi-helper/exists? conn index-name)
    (let [result (es-helper/search
                  conn index-name idx-mapping-type {"_source" "index-set-id"})]
      (map #(-> % :_source :index-set-id) (get-in result [:hits :hits])))))

(defn get-index-sets
  "Fetch all index-sets in elastic."
  [{:keys [conn]} index-name idx-mapping-type]
  (when (esi-helper/exists? conn index-name)
    (let [result (es-helper/search
                  conn index-name idx-mapping-type {"_source" "index-set-request"})]
      (map #(decode-field (get-in % [:_source :index-set-request]))
           (get-in result [:hits :hits])))))

(defn delete-index
  "Delete given elastic index"
  [{:keys [conn config]} index-name]
  (when (esi-helper/exists? conn index-name)
    (let [admin-token (:admin-token config)
          response (client/delete (esr/index-url conn index-name)
                                  {:headers {"Authorization" admin-token
                                             "Confirm-delete-action" "true"}
                                   :throw-exceptions false})
          status (:status response)]
      (if-not (some #{200 202 204} [status])
        (errors/internal-error! (m/index-delete-failure-msg response))))))

(defn save-document-in-elastic
  "Save the document in Elasticsearch, raise error on failure."
  [context es-index es-mapping-type doc-id es-doc]
  (try
    (let [conn (get-in context [:system :db :conn])
          result (es-helper/put conn es-index es-mapping-type doc-id es-doc)
          _ (esi-helper/refresh conn es-index)
          {:keys [error status]} result]
      (when (:error result)
        ;; service layer to rollback index-set create  progress on error
        ;; to result in 503 if replicas setting value of 'indext-sets' is set to > 0 when running on a single node
        (throw (Exception. (format "Save to Elasticsearch failed. Reported status: %s and error: %s " status error)))))
    (catch clojure.lang.ExceptionInfo e
      (let [err-msg (get-in (ex-data e) [:body])
            msg (str "Call to Elasticsearch caught exception " err-msg)]
        (throw (Exception. msg))))))

(defn delete-document
  "Delete the document from elastic, raise error on failure."
  [context index-name mapping-type id]
  (let [{:keys [host port admin-token]} (get-in context [:system :db :config])
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

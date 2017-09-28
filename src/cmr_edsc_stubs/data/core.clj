(ns cmr-edsc-stubs.data.core
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.jdbc :as jdbc]
   [cmr-edsc-stubs.data.sources :as data-sources]
   [cmr-edsc-stubs.util :as util]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]
   [cmr.client.common.util :as client-util])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Raw DB / JDBC Operations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-db
  [system]
  (get-in system [:apps :metadata-db :db :spec]))

(defn query
  [system sql]
  (jdbc/with-db-connection [db-conn (get-db system)]
    (jdbc/query db-conn [sql])))

(defn insert
  [system ^Keyword table data]
  (jdbc/with-db-connection [db-conn (get-db system)]
    (jdbc/insert! db-conn table data)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Metadata DB Operations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-provider
  ([provider-data]
    (create-provider provider-data :local :metadata-db))
  ([provider-data environment-type service-key]
    (let [endpoint (client-util/get-endpoint environment-type service-key)]
      (-> {:body provider-data
           :content-type :json
           :throw-exceptions false
           :headers util/local-token-header}
          ((partial client/post (format "%s/providers" endpoint)))
          (select-keys [:status :body])
          (update-in [:body] #(json/parse-string % true))))))

(defn create-ges-disc-provider
  []
  (create-provider (data-sources/get-ges-disc-provider)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   HTTP Service Operations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ingest-ges-disc-airx3std-collection
  ([]
    (ingest-ges-disc-airx3std-collection :local))
  ([deployment]
    (let [provider-id "GES_DISC"
          submit-content-type "application/echo10+xml"
          accept-content-type "application/json"
          native-id (str (java.util.UUID/randomUUID))
          client (ingest/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})]
      (ingest/create-collection client
                                provider-id
                                native-id
                                (data-sources/get-ges-disc-airx3std-collection)
                                {:content-type submit-content-type
                                 :accept accept-content-type}))))

(defn ingest-ges-disc-airx3std-variables
  ([]
    (ingest-ges-disc-airx3std-variables :local))
  ([deployment]
    (let [provider-id "GES_DISC"
          submit-content-type (str "application/vnd.nasa.cmr.umm+json;"
                                   "version=1.0; charset=UTF-8")
          accept-content-type "application/json"
          client (ingest/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})]
      (for [var-files [(data-sources/get-ges-disc-airx3std-ch4-variables)
                       ;; add more groups of variables here
                       ]]
        (for [var-file var-files]
          (do
            (println "Loading" (str var-file) "...")
            (let [native-id (str (java.util.UUID/randomUUID))]
              (ingest/create-variable client
                                      provider-id
                                      native-id
                                      (slurp var-file)
                                      {:content-type submit-content-type
                                       :accept accept-content-type}))))))))

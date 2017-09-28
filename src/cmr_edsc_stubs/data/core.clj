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
      (client/post (format "%s/providers" endpoint)
       {:body provider-data
        :content-type :json
        :throw-exceptions false
        :headers util/local-token-header}))))

(defn create-ges-disc-provider
  []
  (create-provider (data-sources/get-ges-disc-provider)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   HTTP Service Operations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ingest-ges-disc-airx3std-collection
  []
  (let [client (ingest/create-client {:endpoint :local
                                   :token util/local-token
                                   :return-body? true})]
    (ingest/create-collection client
                              "GES_DISC"
                              "coll-native-id"
                              (data-sources/get-ges-disc-airx3std-collection)
                              {:content-type "application/echo10+xml"
                               :accept "application/json"})))

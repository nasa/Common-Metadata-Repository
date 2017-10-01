(ns cmr-edsc-stubs.data.core
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.jdbc :as jdbc]
   [cmr-edsc-stubs.data.service :as service]
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
;;; These functions all require a running component-system. The intent is
;;; for them to be run in the dev-system REPL.

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

(defn prepared
  [system sql values]
  (jdbc/with-db-connection [db-conn (get-db system)]
    (jdbc/db-do-prepared db-conn sql values)))

(defn ingest-ges-disc-airx3std-opendap-service
  ([system]
    (ingest-ges-disc-airx3std-opendap-service system 1 "S1000000001-GES_DISC"))
  ([system internal-id concept-id]
    (let [edn-data (data-sources/get-ges-disc-airx3std-opendap-service)
          metadata (data-sources/get-ges-disc-airx3std-opendap-service :data)
          sql (str "INSERT INTO CMR_SERVICES "
                   "(transaction_id, created_at, revision_date, id, native_id,"
                   " provider_id, user_id, service_name, deleted,"
                   " format, revision_id, concept_id, metadata) "
                   "VALUES "
                   "(GLOBAL_TRANSACTION_ID_SEQ.NEXTVAL,CURRENT_TIMESTAMP,"
                   "CURRENT_TIMESTAMP,?,?,?,?,?,?,?,?,?,?)")
          values [internal-id (str (java.util.UUID/randomUUID)) "GES_DISC"
                  "cmr-edsc-stubber" (:name edn-data) 0 "application/json" 1
                  concept-id (.getBytes metadata)]]
      (prepared system sql values))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Metadata DB Operations   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; The CMR metadata-db is only accessibly via the localhost, so the intent
;;; with these functions is that they be used against a local instance of
;;; the CMR or be run directly on the systems where they are deployed.

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
;;; Since these functions use the publicly accessible API endpoints for the
;;; published CMR services, they may be run from anywhere.

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

(defn associate-ch4-variables-with-ges-disc-airx3std-collection
  ([]
    (associate-ch4-variables-with-ges-disc-airx3std-collection :local))
  ([deployment]
    (let [provider-id "GES_DISC"
          submit-content-type "application/json"
          accept-content-type "application/json"
          client (search/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})
          vars (search/get-variables client
                                     {:provider provider-id
                                      :page_size 1000}
                                     {:accept accept-content-type})
          cols (search/get-collections client
                                       {:provider provider-id}
                                       {:accept accept-content-type})
          collection-id (:id (get-in cols [:feed :entry 0]))]
      (for [var-id (map :concept_id (:items vars))]
        (search/create-variable-association client
                                            var-id
                                            (json/generate-string
                                             [{:concept_id collection-id}])
                                            {:content-type submit-content-type
                                             :accept accept-content-type})))))

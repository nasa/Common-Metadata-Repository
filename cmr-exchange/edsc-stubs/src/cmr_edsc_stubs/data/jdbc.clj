(ns cmr-edsc-stubs.data.jdbc
  "Raw DB / JDBC Operations

  These functions all require a running component-system. The intent is
  for them to be run in the dev-system REPL."
  (:require
   [clojure.java.jdbc :as jdbc]
   [cmr.sample-data.core :as data-sources])
  (:import
   (clojure.lang Keyword)))

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
                  "cmr-edsc-stubber" (:Name edn-data) 0 "application/json" 1
                  concept-id (.getBytes metadata)]]
      (prepared system sql values))
    {:service-id concept-id}))

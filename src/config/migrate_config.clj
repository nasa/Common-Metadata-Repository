(ns config.migrate-config
  "Provides the configuration for Drift migrations."
  (:require [drift.builder :refer [incremental-migration-number-generator]]
            [clojure.java.jdbc :as j]))

(def db-username (get (System/getenv) "MDB_DB_USERNAME" "METADATA_DB"))
(def db-password (get (System/getenv) "MDB_DB_PASSWORD" "METADATA_DB"))
(def db-host (get (System/getenv) "MDB_DB_HOST" "localhost"))
(def db-port (get (System/getenv) "MDB_DB_PORT" "1521"))
(def db-sid (get (System/getenv) "MDB_DB_SID" "orcl"))
    

(def db {:classname "oracle.jdbc.driver.OracleDriver"
         :subprotocol "oracle"
         :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
         :user db-username
         :password db-password})

(defn- maybe-create-schema-table
  "Creates the schema table if it doesn't already exist."
  [args]
  ;; wrap in a try-catch since there is not easy way to check for the existence of the DB
  (try
    (j/db-do-commands db "CREATE TABLE METADATA_DB.schema_version (version INTEGER NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT sysdate NOT NULL)")
    (catch Exception e)))

(defn current-db-version []
  (println "Start")
  (or (first (j/query db "select version from METADATA_DB.schema_version WHERE rownum = 1 order by created_at DESC")) 0))

(defn update-db-version [version]
  (println "Start 2")
  (j/insert! db "METADATA_DB.schema_version" {:version version})
  #_(j/db-do-commands db "INSERT INTO METADATA_DB.schema_version (version) VALUES (?)" (str version))
  (println "END2"))

(defn migrate-config []
  {:directory "src/migrations/"
   :ns-content "\n  (:require [clojure.java.jdbc :as j])"
   :namespace-prefix "migrations"
   :migration-number-generator incremental-migration-number-generator
   :init maybe-create-schema-table
   :current-version current-db-version
   :update-version update-db-version })

(ns config.migrate-config
  "Provides the configuration for Drift migrations."
  (:require [drift.builder :refer [incremental-migration-number-generator]]
            [clojure.java.jdbc :as j]
            [cmr.oracle.connection :as oracle]))

; (def db-username (get (System/getenv) "MDB_DB_USERNAME" "METADATA_DB"))
; (def db-password (get (System/getenv) "MDB_DB_PASSWORD" "METADATA_DB"))
; (def db-host (get (System/getenv) "MDB_DB_HOST" "localhost"))
; (def db-port (get (System/getenv) "MDB_DB_PORT" "1521"))
; (def db-sid (get (System/getenv) "MDB_DB_SID" "orcl"))


; (def db {:classname "oracle.jdbc.driver.OracleDriver"
;          :subprotocol "oracle"
;          :subname (format "thin:@%s:%s:%s" db-host db-port db-sid)
;          :user db-username
         ; :password db-password})

(def db (oracle/create-db (oracle/db-spec)))

(defn- maybe-create-schema-table
  "Creates the schema table if it doesn't already exist."
  [args]
  ;; wrap in a try-catch since there is not easy way to check for the existence of the DB
  (try
    (j/db-do-commands db "CREATE TABLE METADATA_DB.schema_version (version INTEGER NOT NULL, created_at TIMESTAMP(9) WITH TIME ZONE DEFAULT sysdate NOT NULL)")
    (catch Exception e)))

(defn current-db-version []
  (int (or (:version (first (j/query db ["select version from METADATA_DB.schema_version order by created_at desc"]))) 0)))

(defn update-db-version [version]
  (j/insert! db "METADATA_DB.schema_version" ["version"] [version])
  ; sleep a second to workaround timestamp precision issue
  (Thread/sleep 1000))

(defn migrate-config []
  {:directory "src/migrations/"
   :ns-content "\n  (:require [clojure.java.jdbc :as j]\n            [config.migrate-config :as config])"
   :namespace-prefix "migrations"
   :migration-number-generator incremental-migration-number-generator
   :init maybe-create-schema-table
   :current-version current-db-version
   :update-version update-db-version })

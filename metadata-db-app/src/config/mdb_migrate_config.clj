(ns config.mdb-migrate-config
  "Provides the configuration for Drift migrations."
  (:require
   [clojure.java.jdbc :as j]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.data.oracle.concept-tables :as concept-tables]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as oracle]
   [drift.builder :refer [incremental-migration-number-generator]])
  (:import
   (java.sql SQLException)))

(def db-atom (atom nil))

(defn db
  "Lazily connects to the database and caches it"
  []
  (when-not @db-atom
    (reset! db-atom (lifecycle/start
                      (oracle/create-db (mdb-config/db-spec "metadata-db-migrations")) nil)))
  @db-atom)

(defn- maybe-create-schema-table
  "Creates the schema table if it doesn't already exist."
  [args]
  ;; wrap in a try-catch since there is not easy way to check for the existence of the DB
  (try
    (j/db-do-commands (db) "CREATE TABLE METADATA_DB.schema_version (version INTEGER NOT NULL, created_at TIMESTAMP(9) WITH TIME ZONE DEFAULT sysdate NOT NULL)")
    (catch SQLException e
      ;; 17081 is the error code we get if the table exists and sometimes we also get
      ;; error message for Universal Connection Pool already exists in the Universal Connection Pool Manager
      (when-not (or (= 17081 (.getErrorCode e))
                    (re-matches #"^Unable to start the Universal Connection Pool.*" (.getMessage e)))
        (throw e)))))

(defn current-db-version []
  (int (or (:version (first (j/query (db) ["select version from METADATA_DB.schema_version order by created_at desc"]))) 0)))

(defn update-db-version [version]
  (j/insert! (db) "METADATA_DB.schema_version" ["version"] [version])
  ; sleep a second to workaround timestamp precision issue
  (Thread/sleep 1000))

(defn app-migrate-config []
  "Drift migrate configuration used by CMR app's db-migrate endpoint."
  {:directory "src/cmr/metadata_db/migrations"
   :ns-content "\n  (:require [clojure.java.jdbc :as j]\n            [config.mdb-migrate-config :as config])"
   :namespace-prefix "cmr.metadata-db.migrations"
   :migration-number-generator incremental-migration-number-generator
   :init maybe-create-schema-table
   :current-version current-db-version
   :update-version update-db-version})

(defn migrate-config []
  "Drift migrate configuration used by lein migrate.
   Calling shutdown-agents allows lein migrate command to terminate faster."
  (assoc (app-migrate-config) :finished shutdown-agents))

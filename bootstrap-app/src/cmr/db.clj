(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:require
   [cmr.bootstrap.config :as bootstrap-config]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.sql-utils :as su]
   [cmr.oracle.user :as o]
   [config.bootstrap-migrate-config :as mc]
   [drift.execute :as drift])
  (:gen-class))

(defn create-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (su/ignore-already-exists-errors
      "CMR_BOOTSTRAP user"
      (o/create-user
        db (bootstrap-config/bootstrap-username) (bootstrap-config/bootstrap-password)))))

(defn drop-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (o/drop-user db (bootstrap-config/bootstrap-username))))

(defn -main
  "Execute the given database operation specified by input arguments."
  [& args]
  (info "Running " args)
  (let [op (first args)]
    (try
      (cond
        (= "create-user" op)
        (create-user)

        (= "drop-user" op)
        (drop-user)

        (= "migrate" op)
        (drift/run
          (conj
           (vec args)
           "-c"
           "config.bootstrap_migrate_config/app-migrate-config"))

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (shutdown-agents)
        (System/exit 1))))

  (shutdown-agents)
  (System/exit 0))

(comment
  ;; run this code to create the users necessary for Bootstrap
  ;; integration tests
  (let [db (oracle-config/sys-dba-db-spec)]
    (o/create-user db "DEV_52_CATALOG_REST" "DEV_52_CATALOG_REST")
    (o/grant-select-privileges db "DEV_52_CATALOG_REST" "METADATA_DB")))

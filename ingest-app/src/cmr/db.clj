(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:require [cmr.common.log :refer (debug info warn error)]
            [drift.execute :as drift]
            [cmr.oracle.user :as o]
            [cmr.oracle.config :as oracle-config]
            [cmr.ingest.config :as ingest-config]
            [config.ingest-migrate-config :as mc]
            [cmr.oracle.sql-utils :as su])
  (:gen-class))

(defn create-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (su/ignore-already-exists-errors
      "CMR_INGEST user"
      (o/create-user db (ingest-config/ingest-username) (ingest-config/ingest-password)))))

(defn drop-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (o/drop-user db (ingest-config/ingest-username))))

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
           "config.ingest_migrate_config/app-migrate-config"))

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (shutdown-agents)
        (System/exit 1))))

  (shutdown-agents)
  (System/exit 0))

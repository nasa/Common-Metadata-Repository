(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:import [java.io File])
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.oracle.user :as o]
            [cmr.oracle.config :as oracle-config]
            [cmr.ingest.config :as ingest-config]
            [cmr.oracle.sql-utils :as su]
            [drift.core]
            [drift.execute])
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
        ;; drift looks for migration files within the user.directory, which is /app in service envs.
        ;; Dev dockerfile manually creates /app/cmr-files to store the unzipped cmr jar so that drift
        ;; can find the migration files correctly
        ;; we had to force method change in drift to set the correct path
        (try
          ;; trying non-local path to find drift migration files
          (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/cmr-files")))]
            (drift.execute/run (conj (vec args) "-c" "config.ingest_migrate_config/app-migrate-config")))
          (catch Exception e
            (println "caught exception trying to find migration files in db.clj file for ingest-app. We are probably in local env. Trying local route to migration files...")
            (with-redefs [drift.core/user-directory (fn [] (new File (str (.getProperty (System/getProperties) "user.dir") "/checkouts/ingest-app/src")))]
              (drift.execute/run (conj (vec args) "-c" "config.ingest_migrate_config/app-migrate-config")))
            ))

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (shutdown-agents)
        (System/exit 1))))

  (shutdown-agents)
  (System/exit 0))

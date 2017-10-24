(ns migrations.008-add-name-column-bulk-update-status
  (:require
   [clojure.java.jdbc :as j]
   [config.migrate-config :as config]))

(defn up
  "Migrates the database up to version 8."
  []
  (println "migrations.008-add-name-column-bulk-update-status up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE CMR_INGEST.bulk_update_task_status ADD
                     name VARCHAR(255) DEFAULT '' NOT NULL"))

(defn down
  "Migrates the database up to version 8."
  []
  (println "migrations.008-add-name-column-bulk-update-status up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE CMR_INGEST.bulk_update_task_status DROP COLUMN name"))

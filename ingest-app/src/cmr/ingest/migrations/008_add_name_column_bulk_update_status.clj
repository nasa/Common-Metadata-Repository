(ns cmr.ingest.migrations.008-add-name-column-bulk-update-status
  (:require
   [clojure.java.jdbc :as j]
   [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 8."
  []
  (println "cmr.ingest.migrations.008-add-name-column-bulk-update-status up...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_task_status ADD
                     name VARCHAR(255) DEFAULT NULL")
  (j/db-do-commands (config/db)
                    "UPDATE bulk_update_task_status SET name = task_id
                     WHERE name IS NULL")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_task_status MODIFY
                     (name VARCHAR(255) NOT NULL)"))

(defn down
  "Migrates the database down to version 7."
  []
  (println "cmr.ingest.migrations.008-add-name-column-bulk-update-status down...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_task_status DROP COLUMN name"))

(ns cmr.ingest.migrations.010-add-unique-constraint-bulk-update-status
  (:require
   [clojure.java.jdbc :as j]
   [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 10."
  []
  (println "cmr.ingest.migrations.010-add-unique-constraint-bulk-update-status up...")
  (j/db-do-commands (config/db)
                    "UPDATE bulk_update_task_status
                     SET NAME = TASK_ID") 

  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_task_status 
                     ADD CONSTRAINT BULK_UPDATE_TASK_STATUS_UK 
                     UNIQUE (PROVIDER_ID, NAME)"))

(defn down
  "Migrates the database down to version 9."
  []
  (println "cmr.ingest.migrations.010-add-unique-constraint-bulk-update-status down...")
  (j/db-do-commands (config/db)
                    "ALTER TABLE bulk_update_task_status 
                     DROP CONSTRAINT BULK_UPDATE_TASK_STATUS_UK"))

(ns cmr.ingest.migrations.007-update-bulk-update-tables
  (:require [clojure.java.jdbc :as j]
            [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 7."
  []
  (println "cmr.ingest.migrations.007-update-bulk-update-tables up...")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status 
                                 DROP CONSTRAINT BULK_UPDATE_COLL_STATUS_FK")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_FK 
                                     FOREIGN KEY (TASK_ID)
                                     REFERENCES BULK_UPDATE_TASK_STATUS(TASK_ID)
                                     ON DELETE CASCADE"))

(defn down
  "Migrates the database down from version 7."
  []
  (println "cmr.ingest.migrations.007-update-bulk-update-tables down...")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 DROP CONSTRAINT BULK_UPDATE_COLL_STATUS_FK")
  (j/db-do-commands (config/db) "ALTER TABLE CMR_INGEST.bulk_update_coll_status
                                 ADD CONSTRAINT BULK_UPDATE_COLL_STATUS_FK 
                                     FOREIGN KEY (TASK_ID)
                                     REFERENCES BULK_UPDATE_TASK_STATUS(TASK_ID)"))

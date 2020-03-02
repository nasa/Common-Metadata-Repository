(ns cmr.ingest.migrations.005-create-bulk-update-tables
  (:require [clojure.java.jdbc :as j]
            [config.ingest-migrate-config :as config]))

(defn up
  "Migrates the database up to version 5."
  []
  (println "cmr.ingest.migrations.005-create-bulk-update-tables up...")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_task_status (
                              TASK_ID NUMBER NOT NULL,
                              PROVIDER_ID  VARCHAR(10) NOT NULL,
                              REQUEST_JSON_BODY  BLOB NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE   VARCHAR(255),
                              CONSTRAINT BULK_UPDATE_TASK_STATUS_PK PRIMARY KEY (TASK_ID)
                              )")
  (j/db-do-commands (config/db) "CREATE TABLE CMR_INGEST.bulk_update_coll_status (
                              TASK_ID NUMBER NOT NULL,
                              CONCEPT_ID VARCHAR(255) NOT NULL,
                              STATUS VARCHAR(20),
                              STATUS_MESSAGE  VARCHAR(255),
                              CONSTRAINT BULK_UPDATE_COLL_STATUS_PK PRIMARY KEY (TASK_ID,CONCEPT_ID),
                              CONSTRAINT BULK_UPDATE_COLL_STATUA_FK FOREIGN KEY (TASK_ID)
                              REFERENCES BULK_UPDATE_TASK_STATUS(TASK_ID)
                              )")
  (j/db-do-commands (config/db) "CREATE INDEX idx_blk_upd_tsk_pi ON CMR_INGEST.bulk_update_task_status(PROVIDER_ID)")
  (j/db-do-commands (config/db) "CREATE SEQUENCE CMR_INGEST.task_id_seq"))

(defn down
  "Migrates the database down from version 5."
  []
  (println "cmr.ingest.migrations.005-create-bulk-update-tables down...")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_coll_status")
  (j/db-do-commands (config/db) "DROP TABLE CMR_INGEST.bulk_update_task_status")
  (j/db-do-commands (config/db) "DROP SEQUENCE CMR_INGEST.task_id_seq"))

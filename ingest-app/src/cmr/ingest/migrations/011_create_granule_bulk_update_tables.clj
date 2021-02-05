(ns cmr.ingest.migrations.011-create-granule-bulk-update-tables
  (:require
   [config.ingest-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 11."
  []
  (println "cmr.ingest.migrations.011-create-granule-bulk-update-tables up...")
  (h/sql
   (str "CREATE TABLE CMR_INGEST.granule_bulk_update_tasks ("
        "TASK_ID VARCHAR2(255) NOT NULL, "
        "PROVIDER_ID  VARCHAR2(10) NOT NULL, "
        "NAME VARCHAR2(255) NOT NULL, "
        "REQUEST_JSON_BODY  BLOB NOT NULL, "
        "USER_ID VARCHAR2(30) NOT NULL, "
        "CREATED_AT TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL, "
        "UPDATED_AT TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL, "
        "STATUS VARCHAR2(20), "
        "STATUS_MESSAGE VARCHAR2(255), "
        "CONSTRAINT gbut_pk PRIMARY KEY (TASK_ID), "
        "CONSTRAINT gbut_prov_name UNIQUE (PROVIDER_ID, NAME) USING INDEX "
        "(create unique index gbut_pn_i ON CMR_INGEST.granule_bulk_update_tasks "
        "(PROVIDER_ID, NAME)))"))

  (h/sql
   (str "CREATE TABLE CMR_INGEST.bulk_update_gran_status ("
        "TASK_ID VARCHAR2(255) NOT NULL, "
        "PROVIDER_ID  VARCHAR2(10) NOT NULL, "
        "GRANULE_UR VARCHAR2(250) NOT NULL, "
        "INSTRUCTION VARCHAR2(4000) NOT NULL, "
        "UPDATED_AT TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL, "
        "STATUS VARCHAR2(20), "
        "STATUS_MESSAGE VARCHAR2(2000), "
        "CONSTRAINT bugs_pk PRIMARY KEY (TASK_ID, PROVIDER_ID, GRANULE_UR), "
        "CONSTRAINT bugs_fk FOREIGN KEY (TASK_ID) "
        "REFERENCES CMR_INGEST.granule_bulk_update_tasks(TASK_ID) "
        "ON DELETE CASCADE)"))

  (h/sql "CREATE INDEX gbut_prov_i ON CMR_INGEST.granule_bulk_update_tasks(PROVIDER_ID)")

  (h/sql "CREATE SEQUENCE CMR_INGEST.gran_task_id_seq"))

(defn down
  "Migrates the database down from version 11."
  []
  (println "cmr.ingest.migrations.011-create-granule-bulk-update-tables down...")
  (h/sql "DROP SEQUENCE CMR_INGEST.gran_task_id_seq")
  (h/sql "DROP TABLE CMR_INGEST.bulk_update_gran_status")
  (h/sql "DROP TABLE CMR_INGEST.granule_bulk_update_tasks"))

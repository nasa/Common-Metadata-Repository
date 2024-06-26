(ns cmr.ingest.migrations.012-update-index-bulk-update-gran-status
  (:require
   [config.ingest-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 12."
  []
  (println "cmr.ingest.migrations.012-update-index-bulk-update-gran-status up...")
  (h/sql "alter table CMR_INGEST.bulk_update_gran_status drop constraint bugs_pk drop index")
  (h/sql "alter table CMR_INGEST.bulk_update_gran_status add constraint bugs_pk primary key (TASK_ID, GRANULE_UR)")
  (h/sql "CREATE INDEX bugs_prov_i ON CMR_INGEST.bulk_update_gran_status(PROVIDER_ID)"))

(defn down
  "Migrates the database down from version 12."
  []
  (println "cmr.ingest.migrations.012-update-index-bulk-update-gran-status down...")
  (h/sql "alter table CMR_INGEST.bulk_update_gran_status drop constraint bugs_pk drop index")
  (h/sql "alter table CMR_INGEST.bulk_update_gran_status add constraint bugs_pk primary key (TASK_ID, PROVIDER_ID, GRANULE_UR)")
  (h/sql "DROP INDEX bugs_prov_i"))

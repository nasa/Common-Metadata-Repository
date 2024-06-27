(ns cmr.metadata-db.migrations.090-add-granule-ur-index-to-bulk-update-gran-status
  (:require
   [config.mdb-migrate-helper :as mh]))

(defn up
  "Migrates the database up to version 90."
  []
  (println "cmr.metadata-db.migrations.090-add-granule-ur-index-to-bulk-update-gran-status up...")
  (mh/sql "create index gran_ur_idx ON CMR_INGEST.bulk_update_gran_status (granule_ur)"))

(defn down
  "Migrates the database down from version 90."
  []
  ;; do nothing as we want to keep the new indexes
  (println "cmr.metadata-db.migrations.090-add-granule-ur-index-to-bulk-update-gran-status down..."))

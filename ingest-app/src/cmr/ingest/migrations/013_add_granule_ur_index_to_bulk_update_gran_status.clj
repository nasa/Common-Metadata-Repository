(ns cmr.ingest.migrations.013-add-granule-ur-index-to-bulk-update-gran-status
  (:require
    [config.ingest-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 13."
  []
  (println "cmr.ingest.migrations.013-add-granule-ur-index-to-bulk-update-gran-status up...")
  (h/sql "create index bugs_gran_ur_idx ON CMR_INGEST.bulk_update_gran_status(GRANULE_UR)"))

(defn down
  "Migrates the database down from version 13."
  []
  (println "cmr.ingest.migrations.013-add-granule-ur-index-to-bulk-update-gran-status down...")
  (h/sql "DROP INDEX bugs_gran_ur_idx"))

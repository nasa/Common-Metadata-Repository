(ns cmr.metadata-db.migrations.012-add-sync-delete-work-indexes
  "Creates indexes to improve performance working with sync delete work table"
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  []
  (println "cmr.metadata-db.migrations.012-add-sync-delete-work-indexes up")
  (h/sql "CREATE INDEX sdw_cid_rid ON sync_delete_work(concept_id, revision_id)")
  (h/sql "CREATE INDEX sdw_deleted ON sync_delete_work(deleted)"))

(defn down
  []
  (println "cmr.metadata-db.migrations.012-add-sync-delete-work-indexes down")
  (h/sql "DROP INDEX sdw_cid_rid")
  (h/sql "DROP INDEX sdw_deleted"))
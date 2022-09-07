(ns cmr.metadata-db.migrations.085-add-source-revision-to-association-table
  (:require
   [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 85."
  []
  (println "cmr.metadata-db.migrations.085-rename-variable-associations up...")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS ADD source_revision_id INTEGER NULL")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS MODIFY association_type VARCHAR (80)"))

(defn down
  "Migrates the database down from version 85."
  []
  (println "cmr.metadata-db.migrations.085-rename-variable-associations down...")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS DROP COLUMN source_revision_id")
  (h/sql "ALTER TABLE CMR_ASSOCIATIONS MODIFY association_type VARCHAR (30)"))

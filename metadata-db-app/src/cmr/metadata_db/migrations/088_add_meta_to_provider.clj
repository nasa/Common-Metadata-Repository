(ns cmr.metadata-db.migrations.088-add-meta-to-provider
  (:require
   [config.mdb-migrate-helper :as mh]))

(defn up
  "Migrates the database up to version 88."
  []
  (println "cmr.metadata-db.migrations.088-add-meta-to-provider up...")
  (try
    (mh/sql "alter table METADATA_DB.PROVIDERS ADD metadata BLOB")
    (catch java.sql.BatchUpdateException e
      ;; If the table already has this column we can ignore the error. This could
      ;; happen if a migration was moved down, then back up.
      (when-not (.contains (.getMessage e) "ORA-01735")
        (throw e)))))

(defn down
  "Migrates the database down from version 88."
  []
  ;; do nothing as we don't want to loose the metadata documents when migrating
  ;; down and then back up
  (println "cmr.metadata-db.migrations.087-update-generic-document-name-length down..."))

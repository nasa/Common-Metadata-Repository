(ns cmr.metadata-db.migrations.090-add-granule-delete-revision-index
  (:require
   [clojure.string :as string]
   [config.mdb-migrate-helper :as mh]))

(defn up
  "Migrates the database up to version 90."
  []
  (println "cmr.metadata-db.migrations.090-add-granule-delete-revision-index up...")
  (doseq [table-name (mh/get-concept-tablenames :granule)]
    (try
      (if (string/includes? table-name "SMALL_PROV")
        (mh/sql (format "create index %s_DR ON %s (provider_id, deleted, revision_date)" table-name table-name))
        (mh/sql (format "create index %s_DR ON %s (deleted, revision_date)" table-name table-name)))
      (catch java.sql.BatchUpdateException e
        ;; If the index already exists we can ignore the error. This could
        ;; happen if a migration was moved down, then back up.'
        (when-not (.contains (.getMessage e) "ORA-00955")
          (throw e))))))

(defn down
  "Migrates the database down from version 90."
  []
  ;; do nothing as we want to keep the new indexes
  (println "cmr.metadata-db.migrations.090-add-granule-delete-revision-index down..."))

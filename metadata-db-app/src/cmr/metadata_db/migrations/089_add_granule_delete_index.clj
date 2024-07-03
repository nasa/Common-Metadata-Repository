(ns cmr.metadata-db.migrations.089-add-granule-delete-index
  (:require
   [clojure.string :as string]
   [config.mdb-migrate-helper :as mh]))

;; TODO temp disable this long migration so it does not trigger
(defn up
  "Migrates the database up to version 89."
  []
  (println "cmr.metadata-db.migrations.089-add-granule-delete-index up...")
  (doseq [table-name (mh/get-concept-tablenames :granule)]
    (try
      (if (string/includes? table-name "SMALL_PROV")
        (mh/sql (format "create index %s_DD ON %s (provider_id, deleted, delete_time)" table-name table-name))
        (mh/sql (format "create index %s_DD ON %s (deleted, delete_time)" table-name table-name)))
      (catch java.sql.BatchUpdateException e
        ;; If the index already exists we can ignore the error. This could
        ;; happen if a migration was moved down, then back up.'
        (when-not (.contains (.getMessage e) "ORA-00955")
          (throw e))))))

(defn down
  "Migrates the database down from version 89."
  []
  ;; do nothing as we want to keep the new indexes
  (println "cmr.metadata-db.migrations.089-add-granule-delete-index down..."))

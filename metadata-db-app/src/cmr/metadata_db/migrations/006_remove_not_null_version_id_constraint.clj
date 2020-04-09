(ns cmr.metadata-db.migrations.006-remove-not-null-version-id-constraint
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 6."
  []
  (println "cmr.metadata-db.migrations.006-remove-not-null-version-id-constraint up...")
  (doseq [coll-table (h/get-regular-provider-collection-tablenames)]
    (try
      (h/sql (format "alter table %s modify (VERSION_ID null)" coll-table))
      (catch java.sql.BatchUpdateException e
        ;; If the table doesn't have a null constraint we'll see this error. We can ignore it
        ;; error occurred during batching: ORA-01451: column to be modified to NULL cannot be modified to NULL
        (when-not (.contains (.getMessage e) "ORA-01451")
          (throw e))))))

(defn down
  "Migrates the database down from version 6."
  []
  (println "cmr.metadata-db.migrations.006-remove-not-null-version-id-constraint down...")
  (doseq [coll-table (h/get-regular-provider-collection-tablenames)]
    (h/sql (format "alter table %s modify (VERSION_ID not null)" coll-table))))

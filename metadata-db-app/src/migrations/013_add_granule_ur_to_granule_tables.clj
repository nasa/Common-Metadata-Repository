(ns migrations.013-add-granule-ur-to-granule-tables
  "Adds granule_ur column to granule tables"
  (:require [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 13."
  []
  (println "migrations.013-add-granule-ur-to-granule-tables up...")

  ;; Create the new column for each granule table first - this will be fast. After the column has
  ;; been created, ongoing ingests should succeed even while the rest of this migration runs.
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s add granule_ur VARCHAR(250)" t)))

  ;; Populate the granule_ur column and create an index simultaneous with ongoing ingest.
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "update %s set granule_ur = native_id" t))
    (h/sql (format "alter table %s modify granule_ur NOT NULL" t))
    (h/sql (format "CREATE INDEX idx_%s_ur ON %s(granule_ur)" t t))))

(defn down
  "Migrates the database down from version 13."
  []
  (println "migrations.013-add-granule-ur-to-granule-tables down.")
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s drop column granule_ur" t))))

(ns cmr.metadata-db.migrations.015-add-granule-ur-to-granule-tables
  "Adds granule_ur column to granule tables"
  (:require [config.mdb-migrate-helper :as h]
            [cmr.oracle.sql-utils :as su]))

(defn up
  "Migrates the database up to version 15, adding granule-ur column to provider granule tables."
  []
  (println "cmr.metadata-db.migrations.015-add-granule-ur-to-granule-tables up...")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "alter table %s add granule_ur VARCHAR(250)" t))
    (h/sql (format "CREATE INDEX idx_%s_ur ON %s(granule_ur)" t t))))

(defn down
  "Migrates the database down from version 15, removing the granule-ur column from provider granule
  tables."
  []
  (println "cmr.metadata-db.migrations.015-add-granule-ur-to-granule-tables down.")
  (doseq [t (h/get-regular-provider-granule-tablenames)]
    (h/sql (format "alter table %s drop column granule_ur" t))))

(ns cmr.metadata-db.migrations.066-add-default-to-created-at-in-granule-tables
  "Add default value SYSTIMESTAMP to created_at column in granule tables."
  (:require
   [config.mdb-migrate-helper :as h]))

(defn- add-default-to-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql 
      (format "alter table %s modify created_at DEFAULT SYSTIMESTAMP" 
              t))))

(defn- drop-default-from-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s modify created_at DEFAULT NULL" t))))

(defn up
  "Migrates the database up to version 66."
  []
  (println "cmr.metadata-db.migrations.066-add-default-to-created-at-in-granule-tables up...")
  (add-default-to-created-at))

(defn down
  "Migrates the database down from version 66."
  []
  (println "cmr.metadata-db.migrations.066-add-default-to-created-at-in-granule-tables down...")
  (drop-default-from-created-at))

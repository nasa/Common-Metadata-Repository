(ns cmr.metadata-db.migrations.051-add-created-at-to-granule-tables
  "Adds created_at column to granule tables."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]
   [config.mdb-migrate-config :as config]))

(defn- add-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s add created_at TIMESTAMP WITH TIME ZONE" t))))

(defn- drop-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s drop column created_at" t))))

(defn up
  "Migrates the database up to version 51."
  []
  (println "cmr.metadata-db.migrations.051-add-created-at-to-granule-tables up...")
  (add-created-at))

(defn down
  "Migrates the database down from version 51."
  []
  (println "cmr.metadata-db.migrations.051-add-created-at-to-granule-tables down...")
  (drop-created-at))

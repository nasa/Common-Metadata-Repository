(ns migrations.051-add-created-at-to-granule-tables
  "Adds created_at column to granule tables."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]
   [config.migrate-config :as config]))

(defn- add-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s add created_at TIMESTAMP WITH TIME ZONE DEFAULT NULL" t))))

(defn- get-oldest-revision-date-for-concept-id
  "Retrieve the oldest revision-date for the granule in the given table with the given id"
  [sql-stmt concept-id]
  (->> (j/query (config/db) [sql-stmt concept-id])
       (remove #(= 1 (:deleted %)))
       (map :revision_date)
       first))

(defn up
  "Migrates the database up to version 51."
  []
  (println "migrations.050-add-created-at-to-granule-tables up...")
  (add-created-at))

(defn down
  "Migrates the database down from version 51."
  []
  (println "migrations.050-add-created-at-to-granule-tables down...")
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s drop column created_at" t))))

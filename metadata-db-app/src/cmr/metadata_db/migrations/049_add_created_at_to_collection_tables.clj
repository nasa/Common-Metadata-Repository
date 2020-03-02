(ns cmr.metadata-db.migrations.049-add-created-at-to-collection-tables
  "Adds created_at column to collection tables."
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn- add-created-at 
  []
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s add created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL" t))
    (h/sql (format "CREATE INDEX %s_c_i ON %s(created_at)" t t))))

(defn- revision-date-query-sql
  "Returns the parameterized SQL string for getting revision dates from the given 
  table for a concept-id"
  [table]
  (format "SELECT revision_date FROM %s WHERE concept_id = ? ORDER BY revision_date ASC" table))

(defn- get-oldest-revision-date-for-concept-id
  "Retrieve the oldest revision-date for the collection in the given table with the given id"
  [sql-stmt concept-id]
  (->> (j/query (config/db) [sql-stmt concept-id])
       (map :revision_date)
       first))
       
(defn- set-created-at
  "Sets the value of created_at in collection tables to the earliest revision_date for each collection"
  []
  (doseq [t (h/get-collection-tablenames)
          :let [rev-date-sql (revision-date-query-sql t)]
          result (h/query (format "SELECT DISTINCT concept_id from %s" t))
          :let [{:keys [concept_id id]} result 
                oldest-revision-date (get-oldest-revision-date-for-concept-id rev-date-sql 
                                                                              concept_id)]]
    (j/execute! (config/db) 
                [(format "UPDATE %s SET created_at = ? WHERE concept_id = ?" t) oldest-revision-date concept_id])))

(defn up
  "Migrates the database up to version 49."
  []
  (println "cmr.metadata-db.migrations.049-add-created-at-to-collection-tables up...")
  (add-created-at)
  (set-created-at))

(defn down
  "Migrates the database down from version 49."
  []
  (println "cmr.metadata-db.migrations.049-add-created-at-to-collection-tables down...")
  (doseq [t (h/get-collection-tablenames)]
    (h/sql (format "alter table %s drop column created_at" t))))
(ns migrations.051-add-created-at-to-granule-tables
  "Adds created_at column to granule tables."
  (:require
   [clojure.java.jdbc :as j]
   [config.mdb-migrate-helper :as h]
   [config.migrate-config :as config]))

(defn- add-created-at
  []
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s add created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL" t))
    (h/sql (format "CREATE INDEX %s_c_i ON %s(created_at)" t t))))

(defn- revision-date-query-sql
  "Returns the parameterized SQL string for getting revision dates from the given
  table for a concept-id"
  [table]
  (format "SELECT revision_date FROM %s WHERE concept_id = ? ORDER BY revision_date ASC" table))

(defn- get-oldest-revision-date-for-concept-id
  "Retrieve the oldest revision-date for the granule in the given table with the given id"
  [sql-stmt concept-id]
  (->> (j/query (config/db) [sql-stmt concept-id])
       (map :revision_date)
       first))

(defn- set-created-at
  "Sets the value of created_at in granule tables to the earliest revision_date for each granule"
  []
  (doseq [t (h/get-granule-tablenames)
          :let [rev-date-sql (revision-date-query-sql t)]
          result (h/query (format "SELECT DISTINCT concept_id from %s" t))
          :let [{:keys [concept_id id]} result
                oldest-revision-date (get-oldest-revision-date-for-concept-id rev-date-sql
                                                                              concept_id)]]
    (j/execute! (config/db)
                [(format "UPDATE %s SET created_at = ? WHERE concept_id = ?" t) oldest-revision-date concept_id])))

(defn up
  "Migrates the database up to version 51."
  []
  (println "migrations.050-add-created-at-to-granule-tables up...")
  (add-created-at)
  (set-created-at))

(defn down
  "Migrates the database down from version 51."
  []
  (println "migrations.050-add-created-at-to-granule-tables down...")
  (doseq [t (h/get-granule-tablenames)]
    (h/sql (format "alter table %s drop column created_at" t))))

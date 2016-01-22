(ns migrations.029-add-transaction-id-into-collections
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 29."
  []
  (println "migrations.029-add-transaction-id-into-collections up...")
  (println "Adding transaction_id to SMALL_PROV_COLLECTIONS")
  (h/sql "ALTER TABLE SMALL_PROV_COLLECTIONS ADD transaction_id INTEGER")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (println (str "Adding transaction_id to table " table))
    (h/sql (format "ALTER TABLE %s ADD transaction_id INTEGER"
                   table))))

(defn down
  "Migrates the database down from version 29."
  []
  (println "migrations.029-add-transaction-id-into-collections down...")
  (doseq [table (h/get-all-concept-tablenames :collection)]
    (println (str "Dropping transaction_id from table " table))
    (h/sql (format "ALTER TABLE %s DROP COLUMN transaction_id"
                   table)))
  (h/sql "ALTER TABLE SMALL_PROV_COLLECTIONS DROP COLUMN transaction_id"))

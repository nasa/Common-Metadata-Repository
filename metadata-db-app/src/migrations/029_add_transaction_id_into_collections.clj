(ns migrations.029-add-transaction-id-into-collections
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 29."
  []
  (println "migrations.029-add-transaction-id-into-collections up...")
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
                   table))))
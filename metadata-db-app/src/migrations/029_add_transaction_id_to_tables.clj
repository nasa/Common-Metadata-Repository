(ns migrations.029-add-transaction-id-to-tables
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 29."
  []
  (println "migrations.029-add-transaction-id-to-tables up...")
  (doseq [table (h/get-concept-tablenames)]
    (h/sql (format "ALTER TABLE %s ADD transaction_id INTEGER DEFAULT 0 NOT NULL"
                   table))))

(defn down
  "Migrates the database down from version 29."
  []
  (println "migrations.029-add-transaction-id-to-tables down...")
  (doseq [table (h/get-concept-tablenames)]
    (h/sql (format "ALTER TABLE %s DROP COLUMN transaction_id"
                   table))))

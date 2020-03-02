(ns cmr.metadata-db.migrations.029-add-transaction-id-to-tables
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 29."
  []
  (println "cmr.metadata-db.migrations.029-add-transaction-id-to-tables up...")
  (doseq [table (h/get-concept-tablenames :collection :granule :service :tag :access-group)]
    (h/sql
     (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
     (format "ALTER TABLE %s ADD transaction_id INTEGER DEFAULT 0 NOT NULL" table))))

(defn down
  "Migrates the database down from version 29."
  []
  (println "cmr.metadata-db.migrations.029-add-transaction-id-to-tables down...")
  (doseq [table (h/get-concept-tablenames :collection :granule :service :tag :access-group)]
    (h/sql
     (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
     (format "ALTER TABLE %s DROP COLUMN transaction_id" table))))

(ns migrations.030-create-transaction-id-index
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.oracle.sql-utils :as utils]))

(defn up
  "Migrates the database up to version 30."
  []
  (println "migrations.030-create-transaction-id-index up...")
  (doseq [table (h/get-concept-tablenames)]
    (utils/ignore-already-exists-errors "INDEX"
      (h/sql
       (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
       (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)" table table)))))
(defn down
  "Migrates the database down from version 30."
  []
  (println "030-create-transaction-id-index down...")
  (doseq [table (h/get-concept-tablenames)]
    (h/sql
     (format "LOCK TABLE %s IN EXCLUSIVE MODE" table)
     (format "DROP INDEX %s_crtid" table))))

(ns migrations.030-create-transaction-id-index
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 30."
  []
  (println "migrations.030-create-transaction-id-index up...")
  (doseq [table (h/get-concept-tablenames)]
    (h/sql (format "CREATE INDEX %s_crtid ON %s (concept_id, revision_id, transaction_id)" table table))))

(defn down
  "Migrates the database down from version 30."
  []
  (println "030-create-transaction-id-index down...")
  (doseq [table (h/get-concept-tablenames)]
    (h/sql (format "DROP INDEX %s_crtid" table))))

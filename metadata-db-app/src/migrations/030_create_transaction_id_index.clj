(ns migrations.030-create-transaction-id-index
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections up...")
  (doseq [table (h/get-all-concept-tablenames)]
    (println (format "Adding index for transaction_id to table %s" table))
    (h/sql (format "CREATE INDEX %s_tid ON %s (transaction_id)" table table))))

(defn down
  "Migrates the database down from version 30."
  []
  (println "migrations.030-assign-transaction-ids-to-collections down...")
  (doseq [table (h/get-all-concept-tablenames)]
    (println (format "Removing index for transaction_id from table %s" table))
    (h/sql (format "DROP INDEX %s_tid" table))))
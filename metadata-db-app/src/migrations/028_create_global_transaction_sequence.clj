(ns migrations.028-create-global-transaction-sequence
  (:require [clojure.java.jdbc :as j]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 28."
  []
  (println "migrations.028-create-global-transaction-sequence up...")
  (when-not (h/sequence-exists? "global_transaction_id_seq")
    (let [max-rev-id (reduce (fn [max-rev table]
                               (let [resp (h/query (format "select max(revision_id) as max_rev_id from %s" table))
                                     value (-> resp first :max_rev_id)]
                                 (if value
                                   (max max-rev (long value))
                                   max-rev)))
                             0
                             (h/get-all-concept-tablenames))]
      (h/sql
        (format "CREATE SEQUENCE METADATA_DB.global_transaction_id_seq START WITH %s INCREMENT BY 1 CACHE 400"
                (+ 100000 max-rev-id))))))

(defn down
  "Migrates the database down from version 28."
  []
  (println "migrations.028-create-global-transaction-sequence down...")
  (h/sql "DROP SEQUENCE GLOBAL_TRANSACTION_ID_SEQ"))
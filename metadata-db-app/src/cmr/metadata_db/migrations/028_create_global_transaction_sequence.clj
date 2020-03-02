(ns cmr.metadata-db.migrations.028-create-global-transaction-sequence
  (:require [clojure.java.jdbc :as j]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]))
(defn up
  "Migrates the database up to version 28."
  []
  (println "cmr.metadata-db.migrations.028-create-global-transaction-sequence up...")
  ;; Create a sequences to generate transaction-ids to be called from code when saving concepts.
  ;; A second sequence will be create in migration 31 to be used with the migration. Using two
  ;; sequences allows ingest to continue to update transaction-ids during the migration
  ;; without requiring locking.
  (h/sql
        (format "CREATE SEQUENCE METADATA_DB.global_transaction_id_seq START WITH %s INCREMENT BY 1 CACHE 400"
                h/TRANSACTION_ID_CODE_SEQ_START)))

(defn down
  "Migrates the database down from version 28."
  []
  (println "cmr.metadata-db.migrations.028-create-global-transaction-sequence down...")
  (h/sql "DROP SEQUENCE GLOBAL_TRANSACTION_ID_SEQ"))

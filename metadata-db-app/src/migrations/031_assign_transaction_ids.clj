(ns migrations.031-assign-transaction-ids
  (:require [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.concept-validations :as v]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]))

(defn up
  "Migrates the database up to version 31."
  []
  (println "migrations.031-assign-transaction-ids up...")
  (let [migration-trans-seqeunce-start (inc v/MAX_REVISION_ID)]
    (h/sql
      (format "CREATE SEQUENCE METADATA_DB.migration_transaction_id_seq START WITH %s INCREMENT BY 1 CACHE 400"
              migration-trans-seqeunce-start))
    (doseq [table (h/get-all-concept-tablenames :collection :service :tag :access-group)]
      (println (str "Updating transaction_id in table " table))
      (doseq [row (h/query (format (str "SELECT id FROM %s WHERE transaction_id = 0 "
                                        "ORDER BY concept_id ASC, revision_id ASC FOR UPDATE") table))]
        (h/sql (format "UPDATE %s SET transaction_id=MIGRATION_TRANSACTION_ID_SEQ.NEXTVAL WHERE id=%d"
                       table (long (:id row))))))))

(defn down
  "Migrates the database down from version 31."
  []
  (println "migrations.031-assign-transaction-ids down...")
  (let [code-trans-sequence-start (+ v/MAX_REVISION_ID 1000000001)]
    (doseq [table (h/get-all-concept-tablenames :collection)]
      (h/sql (format "UPDATE %s SET transaction_id=0 WHERE transaction_id < %d"
                     table code-trans-sequence-start)))
    (h/sql "DROP SEQUENCE MIGRATION_TRANSACTION_ID_SEQ")))
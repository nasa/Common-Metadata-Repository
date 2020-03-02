(ns cmr.metadata-db.migrations.031-assign-transaction-ids-for-collections-services-tags-and-access-groups
  (:require [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.concept-validations :as v]
            [config.mdb-migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.migrations.028-create-global-transaction-sequence :as m28]
            [cmr.oracle.sql-utils :as utils]))

;; This migration assigns transaction-ids to all existing concepts except granules (they will be
;; handled by a separate job). In order to avoid collisions with new transaction-ids generated
;; for concepts ingested while this migration is running, and to avoid the need to use locking,
;; this migration creates a separate sequence from the global transaction-id sequence used by the
;; metadata-db code when saving concepts. The migration sequence created here begins half way below
;; the start of the global transaction-id seqeunece. At the time of this writing that would
;; be 1,000,000,000. Since the global transaction-id sequence used by metadata-db starts
;; at 2,000,000,000 and there are less than 400,000,000 concepts at this time, the
;; the transaction-ids assigned to existing concepts by this migration should stay well
;; below those assigned to newer conepts ingested while it is running. This would not be
;; the case if this migration were to be run at a later date with more than 999,999,999 existing
;; concepts.
(defn up
  "Migrates the database up to version 31."
  []
  (println "cmr.metadata-db.migrations.031-assign-transaction-ids-for-collections-services-tags-and-access-groups up...")
  (let [migration-trans-seqeunce-start (inc v/MAX_REVISION_ID)]
    (utils/ignore-already-exists-errors "SEQUENCE"
      (h/sql
        (format "CREATE SEQUENCE METADATA_DB.migration_transaction_id_seq START WITH %s INCREMENT BY 1 CACHE 400"
              migration-trans-seqeunce-start)))
    ;; transaction-ids for granules will be handled as a separate job
    (doseq [table (h/get-concept-tablenames :collection :service :tag :access-group)
            row (h/query (format (str "SELECT id FROM %s WHERE transaction_id = 0 "
                                        "ORDER BY concept_id ASC, revision_id ASC FOR UPDATE") table))]
        (h/sql (format "UPDATE %s SET transaction_id=MIGRATION_TRANSACTION_ID_SEQ.NEXTVAL WHERE id=%d"
                       table (long (:id row)))))))

(defn down
  "Migrates the database down from version 31."
  []
  (println "cmr.metadata-db.migrations.031-assign-transaction-ids-for-collections-services-tags-and-access-groups down...")
  (doseq [table (h/get-concept-tablenames :collection :service :tag :access-group)]
      (h/sql (format "UPDATE %s SET transaction_id=0 WHERE transaction_id < %d"
                     table h/TRANSACTION_ID_CODE_SEQ_START)))
  (h/sql "DROP SEQUENCE MIGRATION_TRANSACTION_ID_SEQ"))

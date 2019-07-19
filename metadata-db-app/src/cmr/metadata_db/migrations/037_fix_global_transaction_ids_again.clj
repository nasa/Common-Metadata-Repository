(ns cmr.metadata-db.migrations.037-fix-global-transaction-ids-again
  (:require [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.services.concept-validations :as v]))

;; NOTE: This migration is the same as 036, with the exception of the start value
;; of the migration_transaction_id_seq sequence.

(defn up
  "Migrates the database up to version 36."
  []
  (println "cmr.metadata-db.migrations.037-fix-global-transaction-ids-again up...")
  ;; This sequence was created by a previous migration, but we want to start over, so we have to
  ;; drop it and re-create it.
  (h/sql "DROP SEQUENCE METADATA_DB.MIGRATION_TRANSACTION_ID_SEQ")
  (h/sql (format "CREATE SEQUENCE METADATA_DB.MIGRATION_TRANSACTION_ID_SEQ
                           START WITH %s
                           INCREMENT BY 1
                           CACHE 400
                           ORDER"
                 ;; This is the only important difference from 036:
                 (inc v/MAX_REVISION_ID)))

  ;; Now we want to update every existing record (except for granules) with a transaction-id
  ;; generated from this sequence.
  (doseq [table (h/get-concept-tablenames :collection :service :tag :tag-association :access-group)
          row (h/query (format "SELECT id FROM %s ORDER BY concept_id ASC, revision_id ASC" table))]
    (h/sql (format "UPDATE %s
                    SET transaction_id = METADATA_DB.MIGRATION_TRANSACTION_ID_SEQ.NEXTVAL
                    WHERE id = %s"
                   table
                   (:id row)))))

(defn down
  "Migrates the database down from version 36."
  []
  (println "cmr.metadata-db.migrations.037-fix-global-transaction-ids-again down...")
  (println "nothing to do: previous transaction-id values cannot be restored"))

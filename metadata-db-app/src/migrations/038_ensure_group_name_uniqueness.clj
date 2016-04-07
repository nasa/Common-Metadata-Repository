(ns migrations.038-ensure-group-name-uniqueness
  (:require [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.services.concept-validations :as v]))

;; NOTE: This migration is the same as 036, with the exception of the start value
;; of the migration_transaction_id_seq sequence.

(defn up
  "Migrates the database up to version 36."
  []
  (println "migrations.038-ensure-group-name-uniqueness up...")
  ;; Lowercase all native_id fields so that we can detect duplicates.
  ;; Will throw an exception if duplicate group names are detected, they must be cleaned up manually.

  ;; to determine if there are any duplicates run this SQL:
    ; select LOWER(native_id), count(native_id)
    ; from METADATA_DB.CMR_GROUPS
    ; group by LOWER(native_id)
    ; having count (LOWER(native_id)) > 1;
  (h/sql "UPDATE METADATA_DB.CMR_GROUPS A SET A.native_id = LOWER(A.native_id)"))

(defn down
  "Migrates the database down from version 36."
  []
  (println "migrations.038-ensure-group-name-uniqueness down...")
  (println "nothing to do: previous transaction-id values cannot be restored"))

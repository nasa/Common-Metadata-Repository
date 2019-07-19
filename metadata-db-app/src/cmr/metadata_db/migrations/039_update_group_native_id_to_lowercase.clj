(ns cmr.metadata-db.migrations.039-update-group-native-id-to-lowercase
  (:require [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.services.concept-validations :as v]))

(defn up
  "Migrates the database up to version 39."
  []
  (println "cmr.metadata-db.migrations.039-update-group-native-id-to-lowercase up...")
  ;; Lowercase all native_id fields so that we can detect duplicates.
  ;; Will throw an exception if duplicate group names are detected, they must be cleaned up manually.

  ;; to determine if there are any duplicates run this SQL:
  ;; select a.native_id, a.concept_id, b.native_id, b.concept_id from cmr_groups a, cmr_groups b
  ;; where lower(a.native_id) = lower(b.native_id) and a.provider_id = b.provider_id and a.concept_id != b.concept_id
  (h/sql "UPDATE METADATA_DB.CMR_GROUPS A SET A.native_id = LOWER(A.native_id)"))

(defn down
  "Migrates the database down from version 39."
  []
  (println "cmr.metadata-db.migrations.039-update-group-native-id-to-lowercase down...")
  (println "nothing to do: previous transaction-id values cannot be restored"))

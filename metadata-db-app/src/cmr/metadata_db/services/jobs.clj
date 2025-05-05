(ns cmr.metadata-db.services.jobs
  (:require
   [cmr.common.jobs :refer [def-stateful-job]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.metadata-db.services.provider-validation :as pv]))

(def EXPIRED_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup expired granules"
  (* 3600 5))

(def OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup old revisions of granules and collections"
  (* 3600 6))

(defn expired-concept-cleanup
  [context]
  (doseq [provider (provider-service/get-providers context)]
    ;; Only granule are cleaned up here. Ingest cleans up expired collections because it
    ;; must remove granules from the index that belong to the collections.
    (try
      (concept-service/delete-expired-concepts context provider :granule)
      (catch Exception e
        (error (format "ExpiredConceptCleanupJob for provider %s failed; error: %s" (:provider-id provider) (.getMessage e)))))))

(def-stateful-job ExpiredConceptCleanupJob
  [_ctx system]
  (expired-concept-cleanup {:system system}))

(defn old-revision-concept-cleanup
  [context]
  ;; cleanup CMR system concepts
  (doseq [type [:acl :tag-association :tag :variable-association :service-association :tool-association]]
    (try
      (concept-service/delete-old-revisions context pv/cmr-provider type)
      (catch Exception e
        (error (format "OldRevisionConceptCleanupJob for concept-type %s failed; error: %s" type (.getMessage e))))))
  ;; cleanup provider specific tables
  (doseq [provider (provider-service/get-providers context)]
    (doseq [type [:collection :granule :variable :service :tool :subscription :access-group]]
      (try
        (concept-service/delete-old-revisions context provider type)
        (catch Exception e
          (error (format "OldRevisionConceptCleanupJob for provider %s concept-type %s failed; error: %s" (:provider-id provider) type (.getMessage e))))))))

(def-stateful-job OldRevisionConceptCleanupJob
  [_ctx system]
  (old-revision-concept-cleanup {:system system}))

;; TODO Jyna -- remove metadata jobs so that they don't run during base case run
(def jobs
  "A list of the jobs for metadata db"
  [
   ;{:job-type ExpiredConceptCleanupJob
   ; :interval EXPIRED_CONCEPT_CLEANUP_INTERVAL}
   ;{:job-type OldRevisionConceptCleanupJob
   ; :interval OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL}
   ])

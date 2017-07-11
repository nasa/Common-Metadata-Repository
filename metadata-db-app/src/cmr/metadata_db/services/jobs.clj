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

(def BULK_UPDATE_STATUS_TABLE_CLEANUP_INTERVAL
  "Number of seconds between job runs to clean up old status rows in the bulk update status tables"
  (* 3600 6))

(defn expired-concept-cleanup
  [context]
  (doseq [provider (provider-service/get-providers context)]
    ;; Only granule are cleaned up here. Ingest cleans up expired collections because it
    ;; must remove granules from the index that belong to the collections.
    (concept-service/delete-expired-concepts context provider :granule)))

(def-stateful-job ExpiredConceptCleanupJob
  [ctx system]
  (expired-concept-cleanup {:system system}))

(defn old-revision-concept-cleanup
  [context]
  (doseq [provider (provider-service/get-providers context)]
    (concept-service/delete-old-revisions context provider :collection)
    (concept-service/delete-old-revisions context provider :granule)
    ;; Rework this as part of CMR-4172
    ; (concept-service/delete-old-revisions context provider :service)
    (concept-service/delete-old-revisions context provider :access-group))
  ;; cleanup tags and tag-associations
  (concept-service/delete-old-revisions context pv/cmr-provider :tag)
  (concept-service/delete-old-revisions context pv/cmr-provider :tag-association))

(def-stateful-job OldRevisionConceptCleanupJob
  [ctx system]
  (old-revision-concept-cleanup {:system system}))

(defn bulkupdate-status-table-cleanup
  "clean up the rows in the bulk-update-task-status table that are older than the 
   configured age"
  [context]
  (concept-service/cleanup-old-bulkupdate-status context)) 

(def-stateful-job BulkUpdateStatusTableCleanupJob
  [ctx system]
  (bulkupdate-status-table-cleanup {:system system}))

(def jobs
  "A list of the jobs for metadata db"
  [{:job-type ExpiredConceptCleanupJob
    :interval EXPIRED_CONCEPT_CLEANUP_INTERVAL}
   {:job-type OldRevisionConceptCleanupJob
    :interval OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL}
   {:job-type BulkUpdateStatusTableCleanupJob
    :interval BULK_UPDATE_STATUS_TABLE_CLEANUP_INTERVAL}])

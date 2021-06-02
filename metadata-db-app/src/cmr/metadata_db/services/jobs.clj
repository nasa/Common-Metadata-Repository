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
    (concept-service/delete-expired-concepts context provider :granule)))

(def-stateful-job ExpiredConceptCleanupJob
  [ctx system]
  (expired-concept-cleanup {:system system}))

(defn old-revision-concept-cleanup
  [context]
  ;; cleanup CMR system concepts
  (concept-service/delete-old-revisions context pv/cmr-provider :acl)
  (concept-service/delete-old-revisions context pv/cmr-provider :tag-association)
  (concept-service/delete-old-revisions context pv/cmr-provider :tag)
  (concept-service/delete-old-revisions context pv/cmr-provider :variable-association)
  (concept-service/delete-old-revisions context pv/cmr-provider :service-association)
  (concept-service/delete-old-revisions context pv/cmr-provider :tool-association)
  ;; cleanup provider specific tables
  (doseq [provider (provider-service/get-providers context)]
    (concept-service/delete-old-revisions context provider :collection)
    (concept-service/delete-old-revisions context provider :granule)
    (concept-service/delete-old-revisions context provider :variable)
    (concept-service/delete-old-revisions context provider :service)
    (concept-service/delete-old-revisions context provider :tool)
    (concept-service/delete-old-revisions context provider :subscription)
    (concept-service/delete-old-revisions context provider :access-group)))

(def-stateful-job OldRevisionConceptCleanupJob
  [ctx system]
  (old-revision-concept-cleanup {:system system}))

(def jobs
  "A list of the jobs for metadata db"
  [{:job-type ExpiredConceptCleanupJob
    :interval EXPIRED_CONCEPT_CLEANUP_INTERVAL}
   {:job-type OldRevisionConceptCleanupJob
    :interval OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL}])

(ns cmr.metadata-db.services.jobs
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.jobs :refer [def-stateful-job]]
            [cmr.metadata-db.data.providers :as provider-db]
            [cmr.metadata-db.data.oracle.providers]
            [cmr.metadata-db.services.concept-service :as srv]))

(def EXPIRED_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup expired granules"
  (* 3600 5))

(def OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup old revisions of granules and collections"
  (* 3600 6))

(def-stateful-job ExpiredConceptCleanupJob
  [ctx system]
  (let [db (:db system)]
    (doseq [provider (provider-db/get-providers db)]
      ;; Only granule are cleaned up here. Ingest cleans up expired collections because it
      ;; must remove granules from the index that belong to the collections.
      (srv/delete-expired-concepts db provider :granule))))

(def-stateful-job OldRevisionConceptCleanupJob
  [ctx system]
  (let [db (:db system)]
    (doseq [provider (provider-db/get-providers db)]
      (srv/delete-old-revisions db provider :collection)
      (srv/delete-old-revisions db provider :granule))))

(def jobs
  "A list of the jobs for metadata db"
  [{:job-type ExpiredConceptCleanupJob
    :interval EXPIRED_CONCEPT_CLEANUP_INTERVAL}
   {:job-type OldRevisionConceptCleanupJob
    :interval OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL}])

(comment

  ;; Manually trigger old revision cleanup job
  (let [db (get-in user/system [:apps :metadata-db :db])]
    (doseq [provider (provider-db/get-providers db)]
      (srv/delete-old-revisions db provider :collection)
      (srv/delete-old-revisions db provider :granule)))


)
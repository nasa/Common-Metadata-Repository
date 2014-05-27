(ns cmr.metadata-db.services.jobs
  (:require [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.jobs :as jobs]
            [clojurewerkz.quartzite.jobs :refer [defjob]]
            [clojurewerkz.quartzite.schedule.calendar-interval :refer [schedule with-interval-in-hours]]
            [clojurewerkz.quartzite.conversion :as c]
            [cmr.metadata-db.oracle :as mo]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.metadata-db.data.providers :as provider-db]
            [cmr.metadata-db.data.oracle.providers]
            [cmr.metadata-db.services.concept-service :as srv]))

(def job-key "jobs.expired.1")

(defjob ExpiredConceptCleanupJob
  [ctx]
  (info "Excuting expired concepts cleanup.")
  (try
    (let [db (mo/get-db)]
      (doseq [provider (provider-db/get-providers db)]
        (srv/delete-expired-concepts db provider :collection)
        (srv/delete-expired-concepts db provider :granule)))
    (catch Throwable e
      (error e "ExpiredConceptCleanupJob caught Exception.")))
  (info "Finished expired concepts cleanup."))

(defn start
  []
  (info "Starting expired concepts cleanup job")
  (qs/initialize)
  (qs/start)
  (if-not (qs/get-job job-key)
    (let [job (jobs/build
                (jobs/of-type ExpiredConceptCleanupJob)
                (jobs/with-identity (jobs/key job-key)))
          trigger (t/build
                    (t/with-identity (t/key "triggers.1"))
                    (t/start-now)
                    (t/with-schedule (schedule
                                       (with-interval-in-hours 1))))]
      (qs/schedule job trigger)))
  (info "Expired concepts cleanup job started."))

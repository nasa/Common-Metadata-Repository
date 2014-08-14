(ns cmr.metadata-db.services.jobs
  (:require [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as t]
            [clojurewerkz.quartzite.jobs :as jobs]
            [clojurewerkz.quartzite.stateful :refer [def-stateful-job]]
            [clojurewerkz.quartzite.schedule.calendar-interval :refer [schedule with-interval-in-seconds]]
            [clojurewerkz.quartzite.conversion :as c]
            [cmr.metadata-db.db-holder :as db-holder]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.metadata-db.data.providers :as provider-db]
            [cmr.metadata-db.data.oracle.providers]
            [cmr.metadata-db.services.concept-service :as srv]
            [clj-time.core :as ct]))

(def expired-job-key "jobs.expired.1")
(def old-revisions-job-key "jobs.old.revisions.1")

(def EXPIRED_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup expired granules and collections"
  (* 3600 5))

(def OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL
  "The number of seconds between jobs run to cleanup old revisions of granules and collections"
  (* 3600 12))

(defn configure-quartz-system-properties
  [db]
  (let [{{:keys [subprotocol subname user password]} :spec} db]
    (System/setProperty
      "org.quartz.dataSource.myDS.URL"
      (str "jdbc:" subprotocol ":" subname))
    (System/setProperty "org.quartz.dataSource.myDS.user" user)
    (System/setProperty "org.quartz.dataSource.myDS.password" password)))


(def-stateful-job ExpiredConceptCleanupJob
  [ctx]
  (info "Executing expired concepts cleanup.")
  (try
    (let [db (db-holder/get-db)]
      (doseq [provider (provider-db/get-providers db)]
        (srv/delete-expired-concepts db provider :collection)
        (srv/delete-expired-concepts db provider :granule)))
    (catch Throwable e
      (error e "ExpiredConceptCleanupJob caught Exception.")))
  (info "Finished expired concepts cleanup."))

(def-stateful-job OldRevisionConceptCleanupJob
  [ctx]
  (info "Executing old revision concepts cleanup.")
  (try
    (let [db (db-holder/get-db)]
      (doseq [provider (provider-db/get-providers db)]
        (srv/delete-old-revisions db provider :collection)
        (srv/delete-old-revisions db provider :granule)))
    (catch Throwable e
      (error e "OldRevisionConceptCleanupJob caught Exception.")))
  (info "Finished old revision concepts cleanup."))

(defn schedule-job
  "Start a quartzite job (stopping existing job first)."
  [job-key job-type interval start-delay trigger-key]
  ;; We delete existing jobs and recreate them
  (when (qs/get-job job-key)
    (qs/delete-job (jobs/key job-key)))
  (let [job (jobs/build
              (jobs/of-type job-type)
              (jobs/with-identity (jobs/key job-key)))
        trigger (t/build
                  (t/with-identity (t/key trigger-key))
                  (t/start-at (-> start-delay ct/seconds ct/from-now))
                  (t/with-schedule (schedule
                                     (with-interval-in-seconds interval))))]
    (qs/schedule job trigger)))

(defn start
  [db]
  (info "Starting expired concepts cleanup job")
  (configure-quartz-system-properties db)
  (qs/initialize)
  (qs/start)
  (schedule-job expired-job-key ExpiredConceptCleanupJob EXPIRED_CONCEPT_CLEANUP_INTERVAL 5 "triggers.1")
  (schedule-job old-revisions-job-key OldRevisionConceptCleanupJob OLD_REVISIONS_CONCEPT_CLEANUP_INTERVAL 10 "triggers.2"))

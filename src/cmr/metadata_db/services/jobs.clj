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
            [cmr.metadata-db.data.oracle.concepts :as concepts]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.metadata-db.services.concept-service :as srv]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as s :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]))

(def job-key "jobs.expired.1")

(defn- delete-expired-concepts
  "Delete concepts that have not been deleted and have a delete-time before now"
  [db provider concept-type]
  (let [expired-concepts (j/with-db-transaction
                           [conn db]
                           (let [table (tables/get-table-name provider concept-type)
                                 stmt (su/build
                                        (select [:*]
                                                (from (as (keyword table) :a))
                                                (where `(and (= :revision-id ~(select ['(max :revision-id)]
                                                                                      (from (as (keyword table) :b))
                                                                                      (where '(= :a.concept-id :b.concept-id))))
                                                             (= :deleted 0)
                                                             (is-not-null :delete-time)
                                                             (< :delete-time :SYSTIMESTAMP)))))]
                             (doall (map (partial concepts/db-result->concept-map concept-type conn provider)
                                         (j/query conn stmt)))))]
    (when-not (empty? expired-concepts)
      (info "Deleting expired" (name concept-type) "concepts: " (map :concept-id expired-concepts)))
    (doseq [coll expired-concepts]
      (let [revision-id (inc (:revision-id coll))
            tombstone (merge coll {:revision-id revision-id :deleted true})]
        (srv/try-to-save db tombstone revision-id)))))

(defjob ExpiredConceptCleanupJob
  [ctx]
  (info "Excuting expired concepts cleanup.")
  (let [db (mo/get-db)]
    (doseq [provider (provider-db/get-providers db)]
      (delete-expired-concepts db provider :collection)
      (delete-expired-concepts db provider :granule)))
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

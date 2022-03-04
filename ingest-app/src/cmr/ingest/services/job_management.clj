(ns cmr.ingest.services.job-management
  "Defines the HTTP URL routes for the ingest API."
  (:require
   [clojurewerkz.quartzite.jobs :as qj]
   [clojurewerkz.quartzite.scheduler :as qs]
   [clojurewerkz.quartzite.triggers :as qt]
   [cmr.ingest.services.jobs :as jobs]))

(defn- get-quartz-scheduler
  "Accepts a context object and returns the associated
   Quartz scheduler."
  [context]
  (get-in context [:system :scheduler :qz-scheduler]))

(defn- trigger-key
  "Accepts a string and returns an org.Quartz.TriggerKey object."
  [key]
  (qt/key key))

(defn- job-key
  "Accepts a string and returns an org.Quartz.JobKey object."
  [key]
  (qj/key key))

(defn- pause-job
  "Accepts the job key as a string and pauses the specified job, 
   returning nil."
  [scheduler key]
  (let [job-key (job-key key)]
    (qs/pause-job scheduler job-key)))

(defn- resume-job
  "Accepts the job key as a string and resumes the specified job, 
   returning nil."
  [scheduler key]
  (let [job-key (job-key key)]
    (qs/resume-job scheduler job-key)))

(defn- get-trigger-state
  "Accepts the trigger key as a string and returns the current state
   as a string corresponding to org.Quartz.Trigger.TriggerState"
  [scheduler key]
  (let [trigger-key (trigger-key key)]
    (str (. scheduler getTriggerState trigger-key))))

(defn get-email-subscription-processing-job-state
  "Accepts the application context and returns the current
   email-subscription-procesing job's state."
  [context]
  (let [sched (get-quartz-scheduler context)
        job-key jobs/EMAIL_SUBSCRIPTION_PROCESSING_JOB_KEY
        trigger-key (str job-key ".trigger")]
    (get-trigger-state sched trigger-key)))

(defn enable-email-subscription-processing-job
  "Accepts the application context and enables the
   email-subscription-processing job."
  [context]
  (let [sched (get-quartz-scheduler context)
        job-key jobs/EMAIL_SUBSCRIPTION_PROCESSING_JOB_KEY]
    (resume-job sched job-key)))

(defn disable-email-subscription-processing-job
  "Accepts the application context and disables the
   email-subscription-processing job."
  [context]
  (let [sched (get-quartz-scheduler context)
        job-key jobs/EMAIL_SUBSCRIPTION_PROCESSING_JOB_KEY]
    (pause-job sched job-key)))

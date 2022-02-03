(ns cmr.ingest.services.job-management
  "Defines the HTTP URL routes for the ingest API."
  (:require
   [clojurewerkz.quartzite.jobs :as qj]
   [clojurewerkz.quartzite.scheduler :as qs]
   [clojurewerkz.quartzite.triggers :as qt]))

(defn get-quartz-scheduler
  [context]
  (get-in context [:system :scheduler :qz-scheduler]))

(defn- trigger-key [key]
  (qt/key key))

(defn- job-key [key]
  (qj/key key))

(defn pause-job [scheduler key]
  (let [job-key (job-key key)]
    (qs/pause-job scheduler job-key)))

(defn resume-job [scheduler key]
  (let [job-key (job-key key)]
    (qs/resume-job scheduler job-key)))

(defn get-trigger-state [scheduler trigger-key]
  (str (. scheduler getTriggerState trigger-key)))

(defn get-job-state [scheduler key]
  (get-trigger-state scheduler key))
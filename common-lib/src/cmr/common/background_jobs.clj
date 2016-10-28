(ns cmr.common.background-jobs
  "Namespace for creating simple background jobs that run at a specified interval in a thread.
  For more complex requirements see the cmr.common.jobs namespace which use Quartz. An alternative
  to Quartz was required for the legacy-services application because it is using a version of Quartz
  which is not compatible with the cmr.common.jobs namespace."
  (:require
   [cmr.common.lifecycle :as lifecycle]))

(defrecord BackgroundJob
  [
   ;; The job-function to run
   job-fn

   ;; The interval (in seconds) to run the job
   job-interval-secs

   ;; A reference to the thread to run the background job
   ^Thread thread-ref]

  lifecycle/Lifecycle

  (start
    [this system]
    (when thread-ref (.interrupt thread-ref))
    (let [thread (Thread.
                   (fn []
                     (try
                      (while (not (.isInterrupted (Thread/currentThread)))
                        (job-fn)
                        (Thread/sleep (* 1000 job-interval-secs)))
                      (catch InterruptedException _))))
          ;; Have to set the thread reference before starting the thread otherwise interrupt fails
          updated-this (assoc this :thread-ref thread)]
      (.start (:thread-ref updated-this))
      updated-this))

  (stop
    [this system]
    (when thread-ref (.interrupt thread-ref))
    (assoc this :thread-ref nil)))

(defn create-background-job
  "Creates a background job using the provided job-fn and how often to run the job in seconds."
  [job-fn job-interval-secs]
  (->BackgroundJob job-fn job-interval-secs nil))

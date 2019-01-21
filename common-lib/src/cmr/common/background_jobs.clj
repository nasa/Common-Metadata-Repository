(ns cmr.common.background-jobs
  "Namespace for creating simple background jobs that run at a specified interval in a thread.
  For more complex requirements see the cmr.common.jobs namespace which use Quartz. An alternative
  to Quartz was required for the legacy-services application because it is using a version of Quartz
  which is not compatible with the cmr.common.jobs namespace.

  Note: We allow start to be called on already started jobs and stop to be called on jobs that are
  not running without returning an error. That way the caller does not need to know the current
  state, they only care that after the call the job is either started or stopped."
  (:require
   [cmr.common.lifecycle :as lifecycle]))

(defn- create-thread-for-background-job
  "Returns a Thread object to use to run the background job."
  [job-fn job-interval-secs]
  (Thread.
   (fn []
     (try
      (while (not (.isInterrupted (Thread/currentThread)))
        (job-fn)
        (Thread/sleep (* 1000 job-interval-secs)))
      (catch InterruptedException _)))))

(defrecord BackgroundJob
  [;; The job-function to run
   job-fn
   ;; The interval (in seconds) to run the job
   job-interval-secs
   ;; A reference to the thread to run the background job
   ^Thread thread-ref]

  lifecycle/Lifecycle

  (start
    [this system]
    (when (= (Thread$State/NEW) (.getState thread-ref))
      (.start thread-ref))
    this)

  (stop
    [this system]
    (when (and (not= (Thread$State/NEW) (.getState thread-ref))
               (not (.isInterrupted thread-ref)))
      (.interrupt thread-ref))
    (if (= (Thread$State/NEW) (.getState thread-ref))
      this
      (->BackgroundJob job-fn
                       job-interval-secs
                       (create-thread-for-background-job job-fn job-interval-secs)))))

(defn create-background-job
  "Creates a background job using the provided job-fn and how often to run the job in seconds."
  [job-fn job-interval-secs]
  (->BackgroundJob job-fn
                   job-interval-secs
                   (create-thread-for-background-job job-fn job-interval-secs)))

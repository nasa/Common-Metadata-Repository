(ns cmr.common.jobs
  "Defines a job scheduler that wraps quartz for defining a job."
  (:require
   [clj-time.core :as t]
   [cmr.common.config :as config :refer [defconfig]]
   [cmr.common.lifecycle :as l]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [clojure.core.async :as async]
   ;; quartzite dependencies
   [clojurewerkz.quartzite.conversion :as qc]
   [clojurewerkz.quartzite.jobs :as qj]
   [clojurewerkz.quartzite.schedule.calendar-interval :as qcal]
   [clojurewerkz.quartzite.schedule.cron :as qcron]
   [clojurewerkz.quartzite.scheduler :as qs]
   [clojurewerkz.quartzite.stateful :as qst]
   [clojurewerkz.quartzite.triggers :as qt])
  (:import
   (org.quartz.impl StdScheduler)))

(defn defjob*
  "The function that does the bulk of the work for the def job macros."
  [qtype-fn jtype args body]
  (when (not= 2 (count args))
    (throw (Exception. "defjob expects two arguments of job-context and system")))
  (let [[job-context-sym system-sym] args
        job-name (name jtype)]
    `(~qtype-fn
       ~jtype
       [~job-context-sym]
       (.setName (Thread/currentThread) ~job-name)
       (info ~(str job-name " starting."))
       (let [start-time# (System/currentTimeMillis)]
         (try
           (let [system-holder-var-name# (get
                                           (qc/from-job-data ~job-context-sym)
                                           "system-holder-var-name")
                 system-holder# (-> system-holder-var-name#
                                    symbol
                                    find-var
                                    var-get)
                 ~system-sym (deref system-holder#)]
             ~@body)
           (catch Throwable e#
             (error e# ~(str job-name " caught exception."))))
         (info ~(str
                  job-name
                  " complete in")
                (- (System/currentTimeMillis) start-time#) "ms")))))

(defmacro defjob
  "Wrapper for quartzite defjob that adds a few extras. The code in defjob
  should take two arguments for the quartz job context and the system. It will
  automatically log when the job starts and stops and catch any exceptions and
  log them.

  Example:
  (defjob CleanupJob
  [ctx system]
  (do-some-cleanup system))"
  [jtype args & body]
  (defjob* `qj/defjob jtype args body))

(defmacro def-stateful-job
  "Defines a job that can only run a single instance at a time across a
  clustered set of applications running quartz. The job will be persisted in
  the database. Has the same other extras as defjob.

  Example:
  (def-stateful-job CleanupJob
  [ctx system]
  (do-some-cleanup system))

  Note that, due to job state being tracked in the database, each new job
  that uses `def-stateful-job` will need first to make schema updates to the
  database."
  [jtype args & body]
  (defjob* `qst/def-stateful-job jtype args body))

(def quartz-clustering-properties
  "A list of quartz properties that will allow it to run in a clustered mode.
  The property names will all start with 'org.quartz.' which is elided for
  brevity."

  {;; Main scheduler properties
   "scheduler.instanceName"  "CMRScheduler"
   "scheduler.instanceId"  "AUTO"
   ;; Thread pool
   "threadPool.class"  "org.quartz.simpl.SimpleThreadPool"
   "threadPool.threadCount"  "25"
   "threadPool.threadPriority"  "5"
   ;; Job Store
   "jobStore.misfireThreshold"  "60000"
   "jobStore.class"  "org.quartz.impl.jdbcjobstore.JobStoreTX"
   "jobStore.driverDelegateClass"  "org.quartz.impl.jdbcjobstore.oracle.OracleDelegate"
   "jobStore.useProperties"  "false"
   "jobStore.dataSource"  "myDS"
   "jobStore.tablePrefix"  "QRTZ_"
   "jobStore.isClustered"  "true"
   "jobStore.clusterCheckinInterval"  "20000"

   ;; Data sources
   "dataSource.myDS.driver"  "oracle.jdbc.OracleDriver"
   "dataSource.myDS.maxConnections"  "5"
   "dataSource.myDS.validationQuery" "select 0 from dual"})

(defn- configure-quartz-clustering-system-properties
  "Configures system properties so that quartz can use the database. This is
  only needed when an application is going to run jobs that should only be run
  on a single instance at any given time."
  [db]
  ;; Configure the static properties
  (doseq [[k v] quartz-clustering-properties]
    (System/setProperty (str "org.quartz." k) v))

  (let [{{:keys [subprotocol subname user password]} :spec} db]
    (System/setProperty
      "org.quartz.dataSource.myDS.URL"
      (str "jdbc:" subprotocol ":" subname))
    (System/setProperty "org.quartz.dataSource.myDS.user" user)
    (System/setProperty "org.quartz.dataSource.myDS.password" password)))

(defconfig default-job-start-delay
  "The start delay of the job in seconds."
  {:default 5
   :type Long})

(defmulti create-trigger
  "Creates a trigger for the given job."
  (fn [job-key job]
    (cond
      (:interval job) :interval
      (:daily-at-hour-and-minute job) :daily-at-hour-and-minute
      :else :default)))

(defmethod create-trigger :default
  [job-key job]
  (errors/internal-error!
    (str "Job could not be scheduled. One of :interval or "
         ":daily-at-hour-and-minute should be set.")))

(defmethod create-trigger :interval
  [job-key {:keys [start-delay interval]}]
  (info (format "Scheduling job %s with interval %s" job-key interval))
  (qt/build
    (qt/with-identity (qt/key (str job-key ".trigger")))
    ;; Set start delay
    (qt/start-at (-> (or start-delay (default-job-start-delay))
                     t/seconds
                     t/from-now))
    (qt/with-schedule
      (qcal/schedule (qcal/with-interval-in-seconds interval)))))

(defmethod create-trigger :daily-at-hour-and-minute
  [job-key {[hour minute] :daily-at-hour-and-minute}]
  (info (format "Scheduling job %s daily at %s:%s" job-key hour minute))
  (qt/build
    (qt/with-identity (qt/key (str job-key ".trigger")))
    (qt/with-schedule (qcron/daily-at-hour-and-minute hour minute))))

(defn- try-to-schedule-job
  "Attempts to schedule a job. Swallows the exception if there is an error.
  Returns true if the job was successfully scheduled and false otherwise."
  [scheduler job-key quartz-job trigger]
  (try
    ;; We delete existing jobs and recreate them
    (when (qs/get-job scheduler job-key)
      (qs/delete-job scheduler (qj/key job-key)))
    (qs/schedule scheduler quartz-job trigger)
    true
    (catch Exception e
      (warn e)
      false)))

(defn- schedule-job
  "Schedules a quartzite job (stopping existing job first). This can fail due
  to race conditions with other nodes trying to schedule the job at the same
  time. We will retry up to 3 times to schedule the job."
  [scheduler system-holder-var-name job]
  (let [{:keys [^Class job-type job-key]} job
        job-key (or job-key (str (.getSimpleName job-type) ".job"))
        quartz-job (qj/build
                    (qj/of-type job-type)
                    (qj/using-job-data
                     {"system-holder-var-name" system-holder-var-name})
                    (qj/with-identity (qj/key job-key)))
        trigger (create-trigger job-key job)]
    (async/go-loop [max-tries 3]
      (when-not (try-to-schedule-job scheduler job-key quartz-job trigger)
        (if (pos? max-tries)
          (do
            (warn (format "Failed to schedule job [%s]. Retrying." job-key))
            ;; Random sleep time to make it less likely that two nodes try to
            ;; recreate the job at the same time. Sleeps between 0.5 seconds
            ;; and 3 seconds.
            (Thread/sleep (+ 500 (rand-int 2500)))
            (recur (dec max-tries)))
          (warn
           (format
            "All retries to schedule job [%s] failed." job-key)))))))

(defprotocol JobRunner
  "Defines functions for pausing and resuming jobs"
  (pause-jobs
    [scheduler]
    "Pauses running of all jobs.")
  (resume-jobs
    [scheduler]
    "Resumes running of all jobs.")
  (paused?
    [scheduler]
    "Returns true if the jobs are paused and false otherwise."))

(defrecord JobScheduler
    [;; A var that will point to an atom to use to contain the system. Jobs need
     ;; access to the system. There can be multiple systems running at once so
     ;; there needs to be a separate var per system as a way for jobs to access
     ;; it at run time.
     system-holder-var
     ;; The key used to store the jobs db in the system
     db-system-key
     ;; A list of maps containing job-type, interval, and optionally start-delay
     jobs
     ;; True or false to indicate it should run in clustered mode.
     clustered?
     ;; true or false to indicate it's running
     running?
     ;; Instance of a quartzite scheduler
     ^StdScheduler qz-scheduler]

  l/Lifecycle

  (start
    [this system]
    (when (:running? this)
      (errors/internal-error! "Job scheduler already running"))
    (if-let [jobs (:jobs this)]
      (let [system-holder-var (:system-holder-var this)
            system-holder (-> system-holder-var find-var var-get)
            system-holder-var-name (str (namespace system-holder-var) "/"
                                        (name system-holder-var))]
        (reset! system-holder system)

        (when (:clustered? this)
          (configure-quartz-clustering-system-properties
           (get system db-system-key)))

        ;; Start quartz
        (try
          (let [scheduler (qs/start (qs/initialize))]

            ;; schedule all the jobs
            (doseq [job jobs]
              (schedule-job scheduler system-holder-var-name job))

            (when (paused? (assoc this :qz-scheduler scheduler))
              (warn "All jobs are currently paused"))

            (assoc this
                   :running? true
                   :qz-scheduler scheduler))
          (catch Exception e
            (error "Could not start scheduler" e)
            (assoc this :running? false))))
      (errors/internal-error! "No jobs to schedule.")))

  (stop
    [this system]
    (when (:running? this)
      ;; Shutdown and wait for jobs to complete
      (qs/shutdown qz-scheduler true)
      (assoc this :running? false :qz-scheduler nil)))

  JobRunner

  (pause-jobs
    [scheduler]
    (qs/pause-all! qz-scheduler)
    (info "Paused all scheduled jobs."))

  (resume-jobs
    [scheduler]
    (qs/resume-all! qz-scheduler)
    (info "Resumed all scheduled jobs."))

  (paused?
    [scheduler]
    (some? (seq (.getPausedTriggerGroups qz-scheduler)))))

;; A scheduler that does not track or run jobs
(defrecord NonRunningJobScheduler
    ;; no fields
    []

  l/Lifecycle

  (start
    [this system]
    this)

  (stop
    [this system]
    this)

  JobRunner

  (pause-jobs
    [scheduler]
    (info "Ignoring request to pause jobs on non running scheduler."))

  (resume-jobs
    [scheduler]
    (info "Ignoring request to resume jobs on non running scheduler."))

  (paused?
    [scheduler]
    (info (str "Ignoring request to check if jobs are paused on non running "
               "scheduler."))
    false))

(defn create-scheduler
  "Starts the quartz job processing. It will look for a sequence in :jobs in
  the system containing a :job-type (a class), :interval keys and optionally
  :start-delay and :job-key. The job-key can be set to override the default in
  cases where you want multiple instances of a job to run with the same type."
  [system-holder-var jobs]
  (map->JobScheduler
   {:system-holder-var system-holder-var
    :jobs jobs
    :clustered? false}))

(defn create-clustered-scheduler
  "Starts the quartz job processing in clustered mode. The system should
  contain :jobs as described in start."
  [system-holder-var db-system-key jobs]
  (map->JobScheduler
   {:system-holder-var system-holder-var
    :db-system-key db-system-key
    :jobs jobs
    :clustered? true}))

(defn create-non-running-scheduler
  "Creates an instance of a scheduler that does not run jobs at all. This is
  useful in situations where an application will need a scheduler instance but
  we do not want jobs to run."
  []
  (->NonRunningJobScheduler))

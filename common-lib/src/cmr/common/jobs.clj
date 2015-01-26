(ns cmr.common.jobs
  "Defines a job scheduler that wraps quartz for defining a job."
  (:require [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.lifecycle :as l]
            [cmr.common.services.errors :as errors]
            [clj-time.core :as t]
            [cmr.common.config :as config]
            ;; quartzite dependencies
            [clojurewerkz.quartzite.scheduler :as qs]
            [clojurewerkz.quartzite.triggers :as qt]
            [clojurewerkz.quartzite.jobs :as qj]
            [clojurewerkz.quartzite.stateful :as qst]
            [clojurewerkz.quartzite.schedule.calendar-interval :as qcal]
            [clojurewerkz.quartzite.schedule.cron :as qcron]
            [clojurewerkz.quartzite.conversion :as qc]))

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
       (info ~(str job-name " starting."))
       (try
         (let [system-holder-var-name# (get (qc/from-job-data ~job-context-sym)
                                            "system-holder-var-name")
               system-holder# (-> system-holder-var-name# symbol find-var var-get)
               ~system-sym (deref system-holder#)]
           ~@body)
         (catch Throwable e#
           (error e# ~(str job-name " caught exception."))))
       (info ~(str job-name " complete.")))))

(defmacro defjob
  "Wrapper for quartzite defjob that adds a few extras. The code in defjob should take two
  arguments for the quartz job context and the system. It will automatically log when the
  job starts and stops and catch any exceptions and log them.

  Example:
  (defjob CleanupJob
  [ctx system]
  (do-some-cleanup system))
  "
  [jtype args & body]
  (defjob* `qj/defjob jtype args body))

(defmacro def-stateful-job
  "Defines a job that can only run a single instance at a time across a clustered set of applications
  running quartz. The job will be persisted in the database. Has the same other extras as defjob.

  Example:
  (def-stateful-job CleanupJob
  [ctx system]
  (do-some-cleanup system))
  "
  [jtype args & body]
  (defjob* `qst/def-stateful-job jtype args body))

(def quartz-clustering-properties
  "A list of quartz properties that will allow it to run in a clustered mode. The property names
  will all start with 'org.quartz.' which is elided for brevity."

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
   "dataSource.myDS.driver"  "oracle.jdbc.driver.OracleDriver"
   "dataSource.myDS.maxConnections"  "5"
   "dataSource.myDS.validationQuery" "select 0 from dual"})

(defn- configure-quartz-clustering-system-properties
  "Configures system properties so that quartz can use the database. This is only needed when an
  application is going to run jobs that should only be run on a single instance at any given time."
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

(def default-start-delay
  (config/config-value-fn :default-job-start-delay "5" #(Long. ^String %)))

(defmulti create-trigger
  "Creates a trigger for the given job."
  (fn [job-key job]
    (cond
      (:interval job) :interval
      (:daily-at-hour-and-minute job) :daily-at-hour-and-minute
      :else :default)))

(defmethod create-trigger :default
  [job-key job]
  (errors/internal-error! (str "Job could not be scheduled. One of :interval or "
                                 ":daily-at-hour-and-minute should be set.")))

(defmethod create-trigger :interval
  [job-key {:keys [start-delay interval]}]
  (info (format "Scheduling job %s with interval %s" job-key interval))
  (qt/build
    (qt/with-identity (qt/key (str job-key ".trigger")))
    ;; Set start delay
    (qt/start-at (-> (or start-delay (default-start-delay))
                     t/seconds
                     t/from-now))
    (qt/with-schedule (qcal/schedule (qcal/with-interval-in-seconds interval)))))

(defmethod create-trigger :daily-at-hour-and-minute
  [job-key {[hour minute] :daily-at-hour-and-minute}]
  (info (format "Scheduling job %s daily at %s:%s" job-key hour minute))
  (qt/build
    (qt/with-identity (qt/key (str job-key ".trigger")))
    (qt/with-schedule (qcron/daily-at-hour-and-minute hour minute))))

(defn- schedule-job
  "Schedules a quartzite job (stopping existing job first)."
  [system-holder-var-name job]
  (let [{:keys [^Class job-type job-key]} job
        job-key (or job-key (str (.getSimpleName job-type) ".job"))
        quartz-job (qj/build
                     (qj/of-type job-type)
                     (qj/using-job-data {"system-holder-var-name" system-holder-var-name})
                     (qj/with-identity (qj/key job-key)))
        trigger (create-trigger job-key job)]
    (when trigger
      ;; We delete existing jobs and recreate them
      (when (qs/get-job job-key)
        (qs/delete-job (qj/key job-key)))
      (qs/schedule quartz-job trigger))))

(defrecord JobScheduler
  [
   ;; A var that will point to an atom to use to contain the system.
   ;; Jobs need access to the system. There can be multiple systems running at once so there needs
   ;; to be a separate var per system as a way for jobs to access it at run time.
   system-holder-var

   ;; A list of maps containing job-type, interval, and optionally start-delay
   jobs
   ;; True or false to indicate it should run in clustered mode.
   clustered?
   ;; true or false to indicate it's running
   running?
   ]

  l/Lifecycle

  (start
    [this system]
    (when (:running? this)
      (errors/internal-error! "Job scheduler already running"))
    (if-let [jobs (:jobs this)]
      (do
        (let [db (or (:jobs-db system) (:db system))
              system-holder-var (:system-holder-var this)
              system-holder (-> system-holder-var find-var var-get)
              system-holder-var-name (str (namespace system-holder-var) "/"
                                          (name system-holder-var))]
          (reset! system-holder system)

          (when (:clustered? this)
            (configure-quartz-clustering-system-properties db))

          ;; Start quartz
          (qs/initialize)
          (qs/start)

          ;; schedule all the jobs
          (doseq [job jobs] (schedule-job system-holder-var-name job)))

        (assoc this :running? true))
      (errors/internal-error! "No jobs to schedule.")))

  (stop
    [this system]
    (when (:running? this)
      ;; Shutdown and wait for jobs to complete
      (qs/shutdown true)
      (assoc this :running? false))))


(defn create-scheduler
  "Starts the quartz job processing. It will look for a sequence in :jobs in the system containing
  a :job-type (a class), :interval keys and optionally :start-delay and :job-key. The job-key can be
  set to override the default in cases where you want multiple instances of a job to run with the
  same type."
  [system-holder-var jobs]
  (->JobScheduler system-holder-var jobs false false))

(defn create-clustered-scheduler
  "Starts the quartz job processing in clustered mode. The system should contain :jobs as described
  in start."
  [system-holder-var jobs]
  (->JobScheduler system-holder-var jobs true false))

(defn pause-jobs
  "Pause all jobs"
  []
  (qs/pause-all!)
  (info "Paused all scheduled jobs.")
  {:status 204})

(defn resume-jobs
  "Resume all jobs"
  []
  (qs/resume-all!)
  (info "Resumed all scheduled jobs.")
  {:status 204})


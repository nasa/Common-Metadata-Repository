(ns cmr.common-app.services.jvm-info
  "Functions to get information about the currently running JVM such as memory usage."
  (:require
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [debug warn info error]])
  (:import
   (java.lang Runtime)))

(defn- get-memory-statistics
  "Returns a map containing the memory statistics for the current JVM process."
  []
  (let [runtime (Runtime/getRuntime)
        free-mb (double (/ (.freeMemory runtime) 1024 1024))
        max-mb (double (/ (.maxMemory runtime) 1024 1024))
        total-mb (double (/ (.totalMemory runtime) 1024 1024))
        used-mb (double (- total-mb free-mb))
        percent-used (double (* 100 (/ used-mb max-mb)))]
    {:free-mb free-mb
     :max-mb max-mb
     :total-mb total-mb
     :used-mb used-mb
     :percent-used percent-used}))

(defn log-jvm-statistics
  "Logs JVM memory statistics."
  []
  (let [{:keys [free-mb max-mb total-mb used-mb percent-used]} (get-memory-statistics)]
    (info (format (str "Maximum Memory (MB): [%.0f] Total Allocated Memory (MB): [%.0f] Free "
                       " memory (MB): [%.0f] Used memory (MB): [%.0f] Percent used: [%.1f]")
                  max-mb total-mb free-mb used-mb percent-used))))

;; Job for logging JVM statistics
(defjob LogJvmStatisticsJob
  [_ _]
  (log-jvm-statistics))

(defconfig log-statistics-interval
  "Number of seconds between logging JVM statistics."
  {:default 300
   :type Long})

(def log-jvm-statistics-job
  "A job to log the JVM statistics."
  {:job-type LogJvmStatisticsJob
   :job-key "log-jvm-statistics"
   :interval (log-statistics-interval)})

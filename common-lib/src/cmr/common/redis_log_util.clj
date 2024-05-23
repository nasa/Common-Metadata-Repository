(ns cmr.common.redis-log-util
  "Consistent logging for redis type logs. This includes consistent messaging for
  cache refreshes and updates."
  (:require
   [cmr.common.log :refer [info]]
   [cmr.common.config :refer [defconfig]]))

(defconfig redis-health-log-time-check
  "We are creating a set of timing logs for redis interactions. Any timing in milliseconds that is greater
  than this setting will create info logs so that we can track when redis is getting near its
  performance threasholds vs just debug timing information. The unit for this setting is milliseconds."
  {:default 100 :type Long})

(defn log-redis-reading-start
  "Helper function provides consistent logs across the CMR when starting to read from a cache."
  ([key]
  (info (format "Reading %s cache" key)))
  ([function-name key]
   (info (format "Reading %s cache in %s" key function-name))))

(defn log-refresh-start
  "Helper function provides consistent logs across the CMR when refreshing caches begin."
  [key]
  (info (format "Refreshing cache %s" key)))

(defn log-update-start
  "Helper function provides consistent logs across the CMR when updating caches begin."
  [key]
  (info (format "Updating cache %s" key)))

(defn log-data-gathering-stats
  "Helper function provides consistent logs across the CMR for redis data gathering durations."
  [function-name key duration]
  (when (> duration (redis-health-log-time-check))
    (info (format "Redis timed function %s for %s data collection time [%d] ms" function-name key duration))))

(defn log-redis-write-complete
  "Helper function provides consistent logs across the CMR for redis write durations."
  [function-name key duration]
  (when (> duration (redis-health-log-time-check))
    (info (format "Redis timed function %s for %s redis write time [%d] ms" function-name key duration))))

(defn log-redis-data-write-complete
  "Helper function provides consistent logs across the CMR for redis data gathering and redis write durations."
  [function-name key stats-duration write-duration]
  (log-data-gathering-stats function-name key stats-duration)
  (log-redis-write-complete function-name key write-duration))

(defn log-redis-read-complete
  "This helper function provides a consistent log across the CMR when finished refreshing caches."
  [function-name key duration]
  (when (> duration (redis-health-log-time-check))
    (info (format "Redis timed function %s for %s redis read time [%d] ms" function-name key duration))))

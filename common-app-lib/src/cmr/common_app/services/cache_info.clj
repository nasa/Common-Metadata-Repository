(ns cmr.common-app.services.cache-info
  "Functions to get information about the currently running JVM such as memory usage."
  (:require
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info]]))

(defjob LogCacheSizesJob
  [_ system]
  (doseq [[cache-key size] (cache/cache-sizes system)]
    (info (format "Cache size [%s] [%d] bytes" cache-key size))))

(defconfig log-cache-info-interval
  "Number of seconds between logging cache information."
  {:default 300
   :type Long})

(def ^:private trim-and-lowercase (comp string/lower-case string/trim name))

(defn create-log-cache-info-job
  "Creates a job to log the system cache info."
  [system-name]
  {:job-type LogCacheSizesJob
   :job-key (format "log-%s-cache-size" (trim-and-lowercase system-name))
   :interval (log-cache-info-interval)})

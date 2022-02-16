(ns cmr.common-app.services.cache-info
  "Functions to get information about the currently running JVM such as memory usage."
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info]]))

(spec/def ::cache-size-map
  (spec/and map?
            #(every? keyword? (keys %))
            #(every? number? (vals %))))

(defn log-cache-sizes
  "Logs the contents of a map of caches and sizes.
  Accepted units #{:bytes :mb :gb} defaults to :bytes

  ```clojure
  (log-cache-sizes (cache/cache-sizes system) :mb)
  ```

  Supports sizes up to
  java.lang.Long/MAX_VALUE = 9223372036854775807 Bytes => 8192 Petabytes"
  ([cache-size-map]
   (log-cache-sizes cache-size-map :bytes))
  ([cache-size-map units]
   (when-not (spec/valid? ::cache-size-map cache-size-map)
     (throw (ex-info "Invalid cache-size-map" 
                     (spec/explain-data ::cache-size-map cache-size-map))))
   (let [[unit unit-scaler] (case units
                              :mb ["MB" 1024.0]
                              :gb ["GB" 1048576.0]
                              ["bytes" 1.0])
         safe-neg? (fnil neg? -1)
         safe-pos? (complement safe-neg?)]
     (doseq [[cache-key size] cache-size-map]
       ;; negatives denote external cache
       (when-not (safe-neg? size)
         (info (format "in-memory-cache [%s] [%.2f %s]" cache-key (/ size unit-scaler) unit))))
     (info (format "Total in-memory-cache usage [%.2f %s]"
                   (/ (reduce + 0 (filter safe-pos? (vals cache-size-map))) unit-scaler)
                   unit)))))

(defjob LogCacheSizesJob
  [_ system]
  (let [cache-size-map (cache/cache-sizes {:system system})]
    (log-cache-sizes cache-size-map :mb)))

(defconfig log-cache-info-interval
  "Number of seconds between logging cache information."
  {:default 900 ;; 15 minutes
   :type Long})

(def ^:private trim-and-lowercase (comp string/lower-case string/trim name))

(defn create-log-cache-info-job
  "Creates a job to log the system cache info."
  [system-name]
  {:job-type LogCacheSizesJob
   :job-key (format "log-%s-cache-size" (trim-and-lowercase system-name))
   :interval (log-cache-info-interval)})

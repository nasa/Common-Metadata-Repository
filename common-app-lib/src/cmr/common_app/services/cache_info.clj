(ns cmr.common-app.services.cache-info
  "Functions to get information about the currently running JVM such as memory usage."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [debug error]]))

;; This is the cache size map validations. Cache keys can either be keywords
;; or strings.
(s/def ::cache-size-map
  (s/and map?
         (fn [m]
           (every? #(or keyword? %
                        string? %) (keys m)))
         #(every? number? (vals %))))

(defn human-readable-bytes
  [size]
  (cond
    (< size 1024)
    (format "%d bytes" size)

    (< size 1048576)
    (format "%.2f KB" (double (/ size 1024)))

    (< size 1073741824)
    (format "%.2f MB" (double (/ size 1048576)))

    :else
    (format "%.2f GB" (double (/ size 1073741824)))))

(defn log-cache-sizes
  "Logs the contents of a map of caches and sizes.

  Supports sizes up to
  java.lang.Long/MAX_VALUE = 9223372036854775807 Bytes => 8192 Petabytes"
  [cache-size-map]
  (when-not (s/valid? ::cache-size-map cache-size-map)
    (throw (ex-info "Invalid cache-size-map"
                    (s/explain-data ::cache-size-map cache-size-map))))
  (doseq [[cache-key size] cache-size-map]
    ;; negatives denote external cache
    (when-not (neg? size)
      ;; This can be a moderate volume log, 20k a day.
      (debug (format "in-memory-cache [%s] [%s] [%d bytes]" cache-key (human-readable-bytes size) size))))
  (try
    (let [combined-size (reduce + 0 (filter pos? (vals cache-size-map)))]
      ;; This is a low volume log, over a 1000 a day, but is paired with the one above.
      (debug (format "Total in-memory-cache usage [%s] [%d bytes]"
                    (human-readable-bytes combined-size)
                    combined-size)))
    (catch java.lang.ArithmeticException e
      (error (str "In-memory-cache size calculation experienced a problem: " (.getMessage e))))))

(defjob LogCacheSizesJob
  [_ system]
  (let [cache-size-map (merge (cache/cache-sizes {:system system})
                              (hash-cache/cache-sizes {:system system}))]
    (log-cache-sizes cache-size-map)))

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

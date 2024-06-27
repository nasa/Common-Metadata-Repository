(ns cmr.common-app.services.cache-info
  "Functions to get information about the currently running JVM such as memory usage."
  (:require
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [cmr.common.cache :as cache]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.common.jobs :refer [defjob]]
   [cmr.common.log :refer [info error]]))

(s/def ::cache-size-map
  (s/and map?
            #(every? keyword? (keys %))
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
      (info (format "in-memory-cache [%s] [%s] [%d bytes]" cache-key (human-readable-bytes size) size))))
  (try
    (let [combined-size (reduce + 0 (filter pos? (vals cache-size-map)))]
      (info (format "Total in-memory-cache usage [%s] [%d bytes]"
                    (human-readable-bytes combined-size)
                    combined-size)))
    (catch java.lang.ArithmeticException e
      (error (str "In-memory-cache size calculation experienced a problem: " (.getMessage e))))))

(def ^:private trim-and-lowercase (comp string/lower-case string/trim name))

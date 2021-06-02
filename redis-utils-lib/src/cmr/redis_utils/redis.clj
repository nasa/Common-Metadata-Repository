(ns cmr.redis-utils.redis
  "Used to implement functions for calling Redis."
  (:require
   [clojure.set :refer [union]]
   [cmr.common.log :refer [error info]]
   [cmr.redis-utils.config :as config]
   [taoensso.carmine :as carmine]))

(defmacro wcar*
  "Safe call redis with conn opts and retries.
  For available commands refer to Redis docuemntation or:
  https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine/commands.edn."
  [& body]
  `(let [with-retry#
         (fn with-retry# [num-retries#]
             (try
               (carmine/wcar (config/redis-conn-opts) ~@body)
               (catch Exception e#
                 (if (> num-retries# 0)
                   (do
                     (info (format "Redis failed with exception %s. Retrying %d more times."
                                   e#
                                   (dec num-retries#)))
                     (Thread/sleep (config/redis-retry-interval))
                     (with-retry# (dec num-retries#)))
                   (error "Redis failed with exception " e#)))))]
     (with-retry# (config/redis-num-retries))))

(defn healthy?
  "Returns true if able to reach Redis."
  [& args]
  {:ok?
   (try
     (= "PONG" (wcar* (carmine/ping)))
     (catch Exception _
       false))})

(defn reset
  "Evict all keys in redis. Primarily for dev-system use."
  []
  (wcar* (carmine/flushall)))

(defn get-keys
  "Scans the redis db for keys matching the match argument (use * to match all).
  Will return up to the max-returned-keys keys. This config option is also used
  as the page size to scan the redis db with. We use this method rather than the
  KEYS redis command to avoid blocking other redis calls as KEYS can become an
  expensive operation."
  ([]
   (get-keys "*"))
  ([match]
   (loop [cursor "0"
          results #{}]
     (let [[new-cursor new-results] (wcar* (carmine/scan cursor :match match :count (config/redis-max-scan-keys)))
           merged-results (union results new-results)]
       (if (or (= "0" (str new-cursor))
               (>= (count merged-results) (config/redis-max-scan-keys)))
         (vec (take (config/redis-max-scan-keys) merged-results))
         (recur new-cursor
                merged-results))))))

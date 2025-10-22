(ns cmr.redis-utils.redis
  "Used to implement functions for calling Redis."
  (:require
   [clojure.set :refer [union]]
   [cmr.common.log :refer [error info]]
   [cmr.redis-utils.config :as config]
   [taoensso.carmine :as carmine]))

  (defmacro wcar*
    "Safe call redis with conn opts and retries.
    The passed in key allows for specific redis caches as well as more logging information.
    The read? is a true/false flag that signifies that a read-only replica redis connection
    should be used. This flag is also used in more logging information.
    For available commands refer to Redis docuemntation or:
    https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine/commands.edn."
    [key read? conn & body]
    `(let [with-retry#
           (fn with-retry# [num-retries#]
             (let [wr-string# (if ~read? "read" "write")]
               (if-not ~conn
                 (do
                   (error (format "Redis %s %s connection is nil, please set it up properly."  wr-string# ~key))
                   (throw (Exception. "Connection to redis server was nil. Please setup properly.")))
                 (try
                   (carmine/wcar ~conn ~@body)
                   (catch Exception e#
                     (if (> num-retries# 0)
                       (do
                         (info (format "Redis %s %s failed with exception %s. Retrying %d more times."
                                       wr-string#
                                       ~key
                                       e#
                                       (dec num-retries#)))
                         (Thread/sleep (config/redis-retry-interval))
                         (with-retry# (dec num-retries#)))
                       (do
                         (error (format "Redis %s %s failed with exception %s"  wr-string# ~key e#))
                         (throw e#))))))))]
       (with-retry# (config/redis-num-retries))))

(defn healthy?
  "Returns true if able to reach Redis."
  [& _args]
  {:ok?
   (try
     (and (= "PONG" (wcar* "healthy?" true (config/redis-read-conn-opts) (carmine/ping)))
          (= "PONG" (wcar* "healthy?" true (config/redis-collection-metadata-cache-read-conn-opts) (carmine/ping))))
     (catch Exception _
       false))})

(defn reset
  "Evict all keys in redis. Primarily for dev-system use."
  ([]
   (wcar* "reset" false (config/redis-conn-opts) (carmine/flushall))
   (wcar* "reset" false (config/redis-collection-metadata-cache-conn-opts) (carmine/flushall)))
  ([conn]
   (wcar* "reset" false conn (carmine/flushall))))

(defn get-keys
  "Scans the redis db for keys matching the match argument (use * to match all).
  Will return up to the max-returned-keys keys. This config option is also used
  as the page size to scan the redis db with. We use this method rather than the
  KEYS redis command to avoid blocking other redis calls as KEYS can become an
  expensive operation."
  ([conn]
   (get-keys "*" conn))
  ([match conn]
   (loop [cursor "0"
          results #{}]
     (let [[new-cursor new-results] (wcar* "get-keys" true conn (carmine/scan cursor :match match :count (config/redis-max-scan-keys)))
           merged-results (union results new-results)]
       (if (or (= "0" (str new-cursor))
               (>= (count merged-results) (config/redis-max-scan-keys)))
         (vec (take (config/redis-max-scan-keys) merged-results))
         (recur new-cursor
                merged-results))))))

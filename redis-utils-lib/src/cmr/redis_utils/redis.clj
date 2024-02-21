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

(defmacro wr-wcar*
  "Safe call redis with conn opts and retries.
  For available commands refer to Redis docuemntation or:
  https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine/commands.edn.
  The retries strategy is using exponential backoff with jitter
  https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/"
  [key read? & body]
  `(let [wr-string# (if ~read? "read" "write")
         with-retry#
         (fn with-retry# [num-retries#]
           (try
             (if ~read?
               (carmine/wcar (config/redis-read-conn-opts) ~@body)
               (carmine/wcar (config/redis-write-conn-opts) ~@body))
             (catch Exception e#
               (if (> num-retries# 0)
                 (do
                   (if ~key
                     (info (format "Redis wr-wcar %s %s failed with exception %s. Retrying %d more times."
                                   wr-string#
                                   ~key
                                   e#
                                   (dec num-retries#)))
                     (info (format "Redis wr-wcar %s failed with exception %s. Retrying %d more times."
                                   wr-string#
                                   (clojure.stacktrace/print-stack-trace e#)
                                   (dec num-retries#))))
                   (Thread/sleep (config/redis-retry-interval))
                   (with-retry# (dec num-retries#)))
                 (error (format "Redis wr-wcar %s %s failed with exception " wr-string# ~key) e#)))))]
     (with-retry# (config/redis-num-retries))))

(defmacro wr-wcar-save*
  "Safe call redis with conn opts and retries.
  For available commands refer to Redis docuemntation or:
  https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine/commands.edn.
  The retries strategy is using exponential backoff with jitter
  https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/"
  [key read? & body]
  `(let [wr-string# (if ~read? "read" "write") 
         with-retry#
         (fn with-retry# [num-retries# last-sleep-time#]
           (try
             (if ~read?
               (carmine/wcar (config/redis-read-conn-opts) ~@body)
               (carmine/wcar (config/redis-write-conn-opts) ~@body))
             (catch Exception e#
               (if (> num-retries# 0)
                 (let [cap# (config/redis-retry-interval-cap)
                       base# (config/redis-retry-interval)
                       sleep-time# (min cap# (+ base# (rand-int (* last-sleep-time# 3))))]
                   (if ~key
                     (info (format "Redis wr-wcar %s %s failed with exception %s. Retrying %d more times. This time waiting [%d] ms."
                                   wr-string#
                                   ~key
                                   e#
                                   (dec num-retries#)
                                   sleep-time#))
                     (info (format "Redis wr-wcar %s failed with exception %s. Retrying %d more times. This time waiting [%d] ms."
                                   wr-string#
                                   (clojure.stacktrace/print-stack-trace e#)
                                   (dec num-retries#)
                                   sleep-time#)))
                   (Thread/sleep sleep-time#)
                   (with-retry# (dec num-retries#) sleep-time#))
                 (error (format "Redis wr-wcar %s %s failed with exception " wr-string# ~key) e#)))))]
     (with-retry# (config/redis-num-retries) (config/redis-retry-interval))))

(defmacro ahah
  [key read? & body]
  (println read?)
  `(let [wr-string# (if ~read? "read" "write")]
     (println "read " ~read?)
     (if ~read?
       (str "read " wr-string# " " ~key " " (config/redis-read-conn-opts) " " ~@body)
       (str "write "wr-string# " " ~key " " (config/redis-write-conn-opts) " " ~@body))))
(comment
  (ahah :hello false (+ 1 1))
  (clojure.stacktrace/print-stack-trace (Exception. "foo"))
  (wr-wcar* :collection-metadata-cache false (carmine/get-map (cmr.redis-utils.redis-cache/serialize :collection-metadata-cache)))
  (-> (wr-wcar* :collection-metadata-cache true :as-pipeline (carmine/hkeys (cmr.redis-utils.redis-cache/serialize :collection-metadata-cache)))
      first)
  )
(defn healthy?
  "Returns true if able to reach Redis."
  [& args]
  {:ok?
   (try
     (= "PONG" (wr-wcar* nil false (carmine/ping)))
     (catch Exception _
       false))})

(defn reset
  "Evict all keys in redis. Primarily for dev-system use."
  []
  (wr-wcar* nil false (carmine/flushall)))

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
     (let [[new-cursor new-results] (wr-wcar* "all" true (carmine/scan cursor :match match :count (config/redis-max-scan-keys)))
           merged-results (union results new-results)]
       (if (or (= "0" (str new-cursor))
               (>= (count merged-results) (config/redis-max-scan-keys)))
         (vec (take (config/redis-max-scan-keys) merged-results))
         (recur new-cursor
                merged-results))))))

;(defn- get-pool
;  "Creates Redis pool connection to be used in queries"
;  [{:keys [host-port db-name]}]
;  (let [pool (doto (ComboPooledDataSource.)
;               (.setDriverClass "com.mysql.cj.jdbc.Driver")
;               (.setJdbcUrl (str "jdbc:mysql://"
;                                 host-port
;                                 "/" db-name))
;               (.setUser username)
;               (.setPassword password)
;                   ;; expire excess connections after 30 minutes of inactivity:
;               (.setMaxIdleTimeExcessConnections (* 30 60))
;                   ;; expire connections after 3 hours of inactivity:
;               (.setMaxIdleTime (* 3 60 60)))]
;    {:datasource pool}))


;(def pool-db (delay (get-pool db-spec)))


;(defn connection [] @pool-db)

; usage in code
;(jdbc/query (connection) ["Select SUM(1, 2, 3)"])
;;https://stackoverflow.com/questions/54613947/connection-pooling-in-clojure
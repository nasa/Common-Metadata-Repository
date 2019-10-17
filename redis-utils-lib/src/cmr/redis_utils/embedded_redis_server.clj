(ns cmr.redis-utils.embedded-redis-server
  "Used to run an in memory redis server."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug info]]
   [cmr.redis-utils.config :as redis-config]
   [taoensso.carmine :as carmine :refer [wcar]])
  (:import
   (redis.embedded RedisExecProvider
                   RedisServer)
   (redis.embedded.util OS)))

(def ^:private redis-server-executable-path
  "redis/src/redis-server")

(defn- create-redis-server-with-settings
  "Create a redis server."
  [^RedisExecProvider exec-provider port]
  (.. (RedisServer/builder)
      (redisExecProvider exec-provider)
      (port (int port))
      (setting "bind 127.0.0.1")
      (setting "appendonly no")
      build))

(defn- create-redis-exec-provider
  "Override default redis executable with one that was installed."
  []
  (.. (RedisExecProvider/defaultProvider)
      (override (OS/UNIX) redis-server-executable-path)
      (override (OS/MAC_OS_X) redis-server-executable-path)
      (override (OS/WINDOWS) redis-server-executable-path)))

(defrecord Redis
  [port]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting redis server on port" port)
    (let [^RedisExecProvider redis-exec-provider (create-redis-exec-provider)
          ^RedisServer redis-server (create-redis-server-with-settings redis-exec-provider port)
          this (assoc this :redis-server redis-server)]
      (.start redis-server)
      this))

  (stop
    [this system]
    (when-let [^RedisServer redis-server (:redis-server this)]
      (debug "Stopping redis server")
      (.stop redis-server))
    (assoc this :redis-server nil)))

(defn create-redis-server
  "Create a redis server."
  ([]
   (create-redis-server (redis-config/redis-port)))
  ([port]
   (->Redis port)))

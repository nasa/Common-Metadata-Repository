(ns cmr.redis-utils.config
  "Contains configuration functions for communicating with Redis"
  (:require
   [cmr.common.api.web-server :as web-server]
   [cmr.common.config :as config :refer [defconfig]]))

(defconfig redis-host
  "Redis host."
  {:default "localhost"})

(defconfig redis-port
  "Port Redis is listening on."
  {:default 6379 :type Long})

(defconfig redis-password
  "Redis password."
  {:default nil})

(defconfig redis-timeout-ms
  "Timeout for connection in ms."
  {:default 3000 :type Long})

(defconfig redis-default-key-timeout-seconds
  "The default value in seconds to use to expire keys. Default is 24 hours."
  {:default 86400 :type Long})

(defconfig redis-max-scan-keys
  "The maximum amount of keys to search for on each iteration of scan aswell as return
  to user."
  {:default 2000 :type Long})

(defconfig redis-num-retries
  "The number of times to retry a failed Redis command."
  {:default 3 :type Long})

(defconfig redis-retry-interval
  "The number of milliseconds to wait between Redis retry command."
  {:default 100 :type Long})

(defn redis-conn-opts
  "Redis connection options to be used with wcar."
  []
  {:spec {:host (redis-host)
          :port (redis-port)
          :password (redis-password)
          :timeout-ms (redis-timeout-ms)}
   :pool {:max-total (web-server/MAX_THREADS)}})

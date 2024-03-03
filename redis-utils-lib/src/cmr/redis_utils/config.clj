(ns cmr.redis-utils.config
  "Contains configuration functions for communicating with Redis"
  (:require
   [cmr.common.api.web-server :as web-server]
   [cmr.common.config :as config :refer [defconfig]]))

(defconfig redis-host
  "Default Redis host."
  {:default "localhost"})

(defconfig redis-read-host
  "Default Redis read-only replication host."
  {:default "localhost"})

(defconfig redis-collection-metadata-host
  "Collection metadata specific cache Redis host."
  {:default "localhost"})

(defconfig redis-collection-metadata-read-host
  "Collection metadata specific Redis read-only replication host."
  {:default "localhost"})

(defconfig redis-port
  "Default port Redis is listening on."
  {:default 6379 :type Long})

(defconfig redis-read-port
  "Default read-only port Redis is listening on."
  {:default 6379 :type Long})

(defconfig redis-collection-metadata-port
  "Collection metadata specific port Redis is listening on."
  {:default 6379 :type Long})

(defconfig redis-collection-metadata-read-port
  "Collection metadata specific read-only port Redis is listening on."
  {:default 6379 :type Long})

(defconfig redis-password
  "Redis password."
  {:default nil})

(defconfig redis-timeout-ms
  "Timeout for connection in ms."
  {:default 3000 :type Long})

(defconfig redis-collection-metadata-timeout-ms
  "5 minute timeout for connection in ms."
  {:default (* 5 60 1000) :type Long})

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
  {:default 400 :type Long})

(defn redis-conn-opts
  "Redis default and write connection options to be used with wcar."
  []
  {:spec {:host (redis-host)
          :port (redis-port)
          :password (redis-password)
          :timeout-ms (redis-timeout-ms)}
   :pool {:max-total (web-server/MAX_THREADS)}})

(defn redis-read-conn-opts
  "Redis default read connection options to be used with wcar."
  []
  {:spec {:host (redis-read-host)
          :port (redis-read-port)
          :password (redis-password)
          :timeout-ms (redis-timeout-ms)}
   :pool {:max-total (web-server/MAX_THREADS)}})

(defn redis-collection-metadata-cache-conn-opts
  "Redis collection-metadata-cache default and write connection options to be used with wcar."
  []
  {:spec {:host (redis-collection-metadata-host)
          :port (redis-collection-metadata-port)
          :password (redis-password)
          :timeout-ms (redis-collection-metadata-timeout-ms)}
   :pool {:max-total (web-server/MAX_THREADS)}})

(defn redis-collection-metadata-cache-read-conn-opts
  "Redis collecltion-metadata-cache read connection options to be used with wcar."
  []
  {:spec {:host (redis-collection-metadata-read-host)
          :port (redis-collection-metadata-read-port)
          :password (redis-password)
          :timeout-ms (redis-collection-metadata-timeout-ms)}
   :pool {:max-total (web-server/MAX_THREADS)}})

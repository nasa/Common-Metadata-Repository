(ns cmr.redis-utils.config
  "Contains configuration functions for communicating with Redis"
  (:require
   [cmr.common.api.web-server :as web-server]
   [cmr.common.config :as config :refer [defconfig]]))

(declare redis-host)
(defconfig redis-host
  "Default Redis host."
  {:default "localhost"})

(declare redis-read-host)
(defconfig redis-read-host
  "Default Redis read-only replication host."
  {:default "localhost"})

(declare redis-collection-metadata-host)
(defconfig redis-collection-metadata-host
  "Collection metadata specific cache Redis host."
  {:default "localhost"})

(declare redis-collection-metadata-read-host)
(defconfig redis-collection-metadata-read-host
  "Collection metadata specific Redis read-only replication host."
  {:default "localhost"})

(declare redis-port)
(defconfig redis-port
  "Default port Redis is listening on."
  {:default 6379 :type Long})

(declare redis-read-port)
(defconfig redis-read-port
  "Default read-only port Redis is listening on."
  {:default 6379 :type Long})

(declare redis-collection-metadata-port)
(defconfig redis-collection-metadata-port
  "Collection metadata specific port Redis is listening on."
  {:default 6379 :type Long})

(declare redis-collection-metadata-read-port)
(defconfig redis-collection-metadata-read-port
  "Collection metadata specific read-only port Redis is listening on."
  {:default 6379 :type Long})

(declare redis-password)
(defconfig redis-password
  "Redis password."
  {:default nil})

(declare redis-timeout-ms)
(defconfig redis-timeout-ms
  "Timeout for connection in ms."
  {:default 3000 :type Long})

(declare redis-collection-metadata-timeout-ms)
(defconfig redis-collection-metadata-timeout-ms
  "5 minute timeout for connection in ms."
  {:default (* 5 60 1000) :type Long})

(declare redis-default-key-timeout-seconds)
(defconfig redis-default-key-timeout-seconds
  "The default value in seconds to use to expire keys. Default is 24 hours."
  {:default 86400 :type Long})

(declare redis-max-scan-keys)
(defconfig redis-max-scan-keys
  "The maximum amount of keys to search for on each iteration of scan aswell as return
  to user."
  {:default 2000 :type Long})

(declare redis-num-retries)
(defconfig redis-num-retries
  "The number of times to retry a failed Redis command."
  {:default 3 :type Long})

(declare redis-retry-interval)
(defconfig redis-retry-interval
  "The number of milliseconds to wait between Redis retry command.
  Some queries take between 200-300 milliseconds to complete. Making this time
  shorter, will cause unnecessary retries."
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

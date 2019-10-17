(ns cmr.redis-utils.config
  "Contains configuration functions for communicating with Redis"
  (:require
   [cmr.common.config :as config :refer [defconfig]]))

(defconfig redis-host
  "Redis host."
  {:default "localhost"})

(defconfig redis-port
  "Port Redis is listening on."
  {:default 6379 :type Long})

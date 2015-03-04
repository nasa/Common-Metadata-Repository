(ns cmr.message-queue.config
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.transmit.config :as tcfg]
            [cheshire.core :as json]))

(defconfig rabbit-mq-port
  "The port to use for connecting to Rabbit MQ"
  {:default 5672 :type Long})

(defconfig rabbit-mq-host
  "The host to use for connecting to Rabbit MQ"
  {:default "localhost"})

(defconfig rabbit-mq-user
  "The username to use when connecting to Rabbit MQ"
  {***REMOVED***})

(defconfig rabbit-mq-password
  "The password for the rabbit mq user."
  {***REMOVED***})

(defconfig rabbit-mq-ttls
  "The Time-To-Live (TTL) for each retry queue (in seconds)."
  {:default [5,50, 500, 5000, 50000]
  ; {:default [1,2,3,4,5]
   :parser #(json/decode ^String %)})

(defn default-config
  "Returns a default config map for connecting to the message queue"
  []
  {:host (rabbit-mq-host)
   :port (rabbit-mq-port)
   :username (rabbit-mq-user)
   :password (rabbit-mq-password)})
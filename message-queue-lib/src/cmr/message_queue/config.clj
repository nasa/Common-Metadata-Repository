(ns cmr.message-queue.config
  (:require [cmr.common.config :as cfg]
            [cmr.transmit.config :as tcfg]))

(def rabbit-mq-port (cfg/config-value-fn :rabbit-mq-port 5672 tcfg/parse-port))

(def rabbit-mq-host
  (cfg/config-value-fn :rabbit-mq-host "localhost"))

(def rabbit-mq-username
  "The name of the user to use when connecting"
  (cfg/config-value-fn :rabbit-mq-user "cmr"))

(def rabbit-mq-password
  "The password to use when connecting"
  (cfg/config-value-fn :rabbit-mq-password "cmr"))

(def rabbit-mq-max-retries
  "The maximum number of times a message will be retried"
  (cfg/config-value-fn :rabbit-mq-max-retries "5" #(Long. ^String %)))

(def rabbit-mq-ttl-base
  "The starting Time-To-Live (TTL) for retried messages. The TTL grows geometrically with each retry"
  (cfg/config-value-fn :rabbit-mq-ttl-base "5000" #(Long. ^String %)))

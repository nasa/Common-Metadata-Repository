(ns cmr.message-queue.config
  (:require [cmr.common.config :as cfg]
            [cmr.transmit.config :as tcfg]
            [cheshire.core :as json]))

(def rabbit-mq-port (cfg/config-value-fn :rabbit-mq-port 5672 tcfg/parse-port))

(def rabbit-mq-host
  (cfg/config-value-fn :rabbit-mq-host "localhost"))

(def rabbit-mq-username
  "The name of the user to use when connecting"
  (cfg/config-value-fn :rabbit-mq-user "cmr"))

(def rabbit-mq-password
  "The password to use when connecting"
  (cfg/config-value-fn :rabbit-mq-password "cmr"))

(def rabbit-mq-ttls
  "The Time-To-Live (TTL) for each retry queue (in seconds)."
  (cfg/config-value-fn :rabbit-mq-ttls "[5,50, 500, 5000, 50000]" #(json/decode ^String %)))

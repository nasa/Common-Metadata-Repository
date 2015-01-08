(ns cmr.message_queue.config
  (:require [cmr.common.config :as cfg]
            [cmr.transmit.config :as tcfg]))

(def rabbit-mq-port (cfg/config-value-fn :rabbit-mq-port 5672 tcfg/parse-port))

(def rabbit-mq-host
  (cfg/config-value-fn :rabbit-mq-host "localhost"))

(def exchange-name
  "The name of the queue exchange to use to retrieve messages"
  (cfg/config-value :indexer-queue-exchange "indexer.exchange"))

(def queue-name
  "The name of the queue to use to retrieve messages"
  (cfg/config-value :indexer-queue-name "indexer.queue"))

(def queue-channel-count
  "The number of channels to use to retreive messgages. There should be one channel
  per worker."
  (cfg/config-value :queue-channel-count 4))
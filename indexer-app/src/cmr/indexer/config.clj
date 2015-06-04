(ns cmr.indexer.config
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig index-queue-name
  "The queue containing ingest events for the indexer"
  {:default "cmr_index.queue"})

(defconfig ingest-exchange-name
  "The ingest exchange to which messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the indexer application."
  []
  (assoc (rmq-conf/default-config)
         :queues [(index-queue-name)]
         :exchanges [(ingest-exchange-name)]
         :queues-to-exchanges {(index-queue-name)
                               (ingest-exchange-name)}))

(defconfig indexer-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

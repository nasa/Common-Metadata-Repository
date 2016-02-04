(ns cmr.access-control.config
  "Contains functions to retrieve access control specific configuration"
  (:require [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.message-queue.config :as rmq-conf]))

(defconfig access-control-exchange-name
  "The access control exchange to which update/save messages are published for access control data."
  {:default "cmr_access_control.exchange"})

(defconfig provider-exchange-name
  "The ingest exchange to which provider change messages are published."
  {:default "cmr_ingest_provider.exchange"})

(defconfig index-queue-name
  "The queue containing ingest events for access control"
  {:default "cmr_access_control_index.queue"})

(defconfig index-queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defn rabbit-mq-config
  "Returns the rabbit mq configuration for the metadata db application."
  []
  (assoc (rmq-conf/default-config)
         :queues [(index-queue-name)]
         :exchanges [(access-control-exchange-name)]
         :queues-to-exchanges {(index-queue-name) [(access-control-exchange-name)
                                                   (provider-exchange-name)]}))




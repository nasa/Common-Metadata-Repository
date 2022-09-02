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

(defconfig provider-queue-name
  "The queue containing provider events"
  {:default "cmr_access_control_provider.queue"})

(defconfig index-queue-name
  "The queue containing ingest events for access control"
  {:default "cmr_access_control_index.queue"})

(defconfig concept-ingest-exchange-name
  "The ingest exchange to which collection and granule change messages are published."
  {:default "cmr_ingest.exchange"})

(defconfig index-queue-listener-count
  "Number of worker threads to use for the queue listener"
  {:default 5
   :type Long})

(defconfig sync-entry-titles-concept-ids-collection-batch-size
  "Batch size used for searching collections when syncing entry-titles and concept-ids"
  {:default 100
   :type Long})

(defconfig enable-edl-groups
  "Flag that indicates if we accept EDL Group Names as group_id identifiers and that they will be used.
   when determining SIDs."
  {:default false :type Boolean})

(defconfig enable-cmr-group-sids
  "Flag that indicates if we include CMR groups when looking up user SIDs."
  {:default true :type Boolean})

(defn queue-config
  "Returns the queue configuration for the application."
  []
  (assoc (rmq-conf/default-config)
         :queues [(index-queue-name) (provider-queue-name)]
         :exchanges [(access-control-exchange-name) (provider-exchange-name)
                     (concept-ingest-exchange-name)]
         :queues-to-exchanges {(index-queue-name) [(access-control-exchange-name)
                                                   (concept-ingest-exchange-name)]
                               (provider-queue-name) [(provider-exchange-name)]}))

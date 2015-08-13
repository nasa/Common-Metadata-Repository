(ns cmr.indexer.services.metadata-db-event-handler
  "Provides functions related to handling events from Metadata DB."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.indexer.config :as config]
            [cmr.indexer.services.index-service :as indexer]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]))

(defn handle-deleted-collection-revision-event
  "Handle the various actions that can be requested via the indexing queue"
  [context {:keys [concept-id revision-id]}]
  (indexer/force-delete-collection-revision context concept-id revision-id))

(defn subscribe-to-metadata-db-events
  "Subscribe to messages related to metadata db"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/deleted-collection-revision-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/deleted-collection-revision-queue-name)
                       #(handle-deleted-collection-revision-event context %)))))


(ns cmr.indexer.services.ingest-event-handler
  "Provides functions related to subscribing to the indexing queue. Creates
  separate subscriber threads to listen on the indexing queue for index requests
  with start-queue-message-handler and provides a multi-method, handle-ingest-event,
  to actually process the messages."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.indexer.config :as config]
            [cmr.indexer.services.index-service :as indexer]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]))

(defmulti handle-ingest-event
  "Handle the various actions that can be requested via the indexing queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-ingest-event :default
  [_ _]
  ;; Default ignores the ingest event. There may be ingest events we don't care about.
  )

(defmethod handle-ingest-event :concept-update
  [context {:keys [concept-id revision-id]}]
  (indexer/index-concept context concept-id revision-id {:ignore-confict? true}))

(defmethod handle-ingest-event :concept-delete
  [context {:keys [concept-id revision-id]}]
  (indexer/delete-concept context concept-id revision-id true))

(defmethod handle-ingest-event :provider-delete
  [context {:keys [provider-id]}]
  (indexer/delete-provider context provider-id))

(defn subscribe-to-ingest-events
  "Subscribe to messages on the indexing queue."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])
        queue-name (config/index-queue-name)]
    (dotimes [n (config/queue-listener-count)]
      (queue/subscribe queue-broker queue-name #(handle-ingest-event context %)))))


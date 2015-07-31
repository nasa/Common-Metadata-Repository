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
  (fn [context all-revisions-index? msg]
    (keyword (:action msg))))

(defmethod handle-ingest-event :default
  [_ _ _]
  ;; Default ignores the ingest event. There may be ingest events we don't care about.
  )

(defmethod handle-ingest-event :concept-update
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (indexer/index-concept context concept-id revision-id {:ignore-confict? true
                                                         :all-revisions-index? all-revisions-index?}))

(defmethod handle-ingest-event :concept-delete
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (indexer/delete-concept context concept-id revision-id true))

(defmethod handle-ingest-event :provider-delete
  [context all-revisions-index? {:keys [provider-id]}]
  (indexer/delete-provider context provider-id))

(defn subscribe-to-ingest-events
  "Subscribe to messages on the indexing queue."
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/index-queue-name)
                       #(handle-ingest-event context false %))
      (queue/subscribe queue-broker
                       (config/all-revisions-index-queue-name)
                       #(handle-ingest-event context true %)))))


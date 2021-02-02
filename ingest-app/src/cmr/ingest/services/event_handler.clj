(ns cmr.ingest.services.event-handler
 (:require
  [cmr.ingest.config :as config]
  [cmr.ingest.services.bulk-update-service :as bulk-update]
  [cmr.message-queue.queue.queue-protocol :as queue-protocol]))

(defmulti handle-provider-event
  "Handle the various actions that can be requested for a provider via the provider-queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-provider-event :bulk-update
  [context msg]
  (bulk-update/handle-bulk-update-event
   context
   (:provider-id msg)
   (:task-id msg)
   (:bulk-update-params msg)
   (:user-id msg)))

(defmethod handle-provider-event :collection-bulk-update
  [context msg]
  (bulk-update/handle-collection-bulk-update-event
   context
   (:provider-id msg)
   (:task-id msg)
   (:concept-id msg)
   (:bulk-update-params msg)
   (:user-id msg)))

;; Default ignores the provider event. There may be provider events we don't care about.
(defmethod handle-provider-event :default
  [_ _])

(defn- handle-gran-bulk-update-event
  [context msg]
  ;; place holder function to be implemented later
  )

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/ingest-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/ingest-queue-name)
                                #(handle-provider-event context %)))
    (dotimes [n (config/bulk-update-queue-listener-count)]
      (queue-protocol/subscribe queue-broker
                                (config/bulk-update-queue-name)
                                #(handle-gran-bulk-update-event context %)))))

(ns cmr.ingest.services.event-handler
 (:require
  [cmr.ingest.config :as config]
  [cmr.message-queue.services.queue :as queue]))

(defmulti handle-provider-event
  "Handle the various actions that can be requested for a provider via the provider-queue"
  (fn [context msg]
    (keyword (:action msg))))

(defmethod handle-provider-event :bulk-update
  [context msg]
  (proto-repl.saved-values/save 25))

;; Default ignores the provider event. There may be provider events we don't care about.
(defmethod handle-provider-event :default
  [_ _])

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/provider-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/provider-queue-name)
                       #(handle-provider-event context %)))))

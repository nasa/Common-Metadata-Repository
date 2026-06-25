(ns cmr.ingest.data.realtime-events
  "Publishing helpers for realtime metadata events emitted by ingest-app."
  (:require
   [cmr.ingest.config :as config]
   [cmr.message-queue.realtime.event :as realtime-event]
   [cmr.message-queue.services.queue :as queue]))

(defn publish-realtime-event
  "Publishes a realtime metadata event to the existing ingest exchange.

  A future implementation may move this to a dedicated realtime exchange or durable event log.
  Keeping the first skeleton on the existing exchange lets indexer-app adopt it incrementally."
  [context event]
  (let [event (realtime-event/normalize event)
        errors (realtime-event/validation-errors event)]
    (when (seq errors)
      (throw (ex-info "Invalid realtime event." {:errors errors :event event})))
    (queue/publish-message
     (get-in context [:system :queue-broker])
     (config/ingest-exchange-name)
     (realtime-event/indexer-message event))))

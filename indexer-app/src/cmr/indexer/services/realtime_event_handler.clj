(ns cmr.indexer.services.realtime.event-handler
  "Realtime event projection hooks for indexer-app.

  This namespace deliberately contains orchestration stubs, not Elasticsearch mapping details.
  The real implementation should reuse existing index-service functions and concept mapping code."
  (:require
   [cmr.common.log :refer [info warn]]
   [cmr.message-queue.realtime.event :as realtime-event]))

(defn- event->index-doc
  "Projects a realtime event into fields that search-app can query.

  These fields should eventually be added to the granule Elasticsearch mapping."
  [event]
  {:concept-id (:concept-id event)
   :provider-id (:provider-id event)
   :collection-concept-id (:collection-concept-id event)
   :native-id (:native-id event)
   :realtime true
   :stream-state (:stream-state event)
   :validation-state (:validation-state event)
   :event-sequence (:sequence event)
   :latest-event-time (:occurred-at event)
   :links (:links event)
   :metadata (:metadata event)})

(defn project-realtime-event
  "Projection boundary for realtime metadata.

  Replace the body with calls into the existing index-service once realtime fields and mappings
  are added. The return value is shaped for tests and logging."
  [_context event]
  (event->index-doc event))

(defn handle-realtime-event
  "Handles the `:realtime-granule-event` ingest action from indexer-app event_handler.clj."
  [context _all-revisions-index? {:keys [event]}]
  (let [errors (realtime-event/validation-errors event)]
    (if (seq errors)
      (warn "Ignoring invalid realtime event:" errors)
      (do
        (info "Projecting realtime event" (:event-id event) (:event-type event))
        (project-realtime-event context event)))))


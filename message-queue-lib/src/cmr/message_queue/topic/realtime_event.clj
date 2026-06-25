(ns cmr.message-queue.topic.realtime-event
  "Shared envelope helpers for realtime CMR metadata events.

  This namespace is intentionally transport-neutral. The existing message-queue-lib decides
  whether events are carried by memory queues, AWS queues, or a future durable log."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]))

(def envelope-version 1)

(def event-types
  #{:granule-created
    :granule-chunk-available
    :granule-updated
    :granule-closed
    :granule-retracted
    :quality-updated})

(def validation-states
  #{:received
    :schema-valid
    :metadata-valid
    :spatial-temporal-valid
    :science-quality-pending
    :certified
    :failed
    :retracted})

(def stream-states
  #{:planned
    :open
    :partial
    :closed
    :superseded
    :retracted})

(def required-keys
  #{:event-id
    :event-type
    :provider-id
    :collection-concept-id
    :native-id
    :occurred-at})

(defn- blank? [value]
  (or (nil? value)
      (and (string? value) (string/blank? value))))

(defn validation-errors
  "Returns a sequence of human-readable validation errors for a realtime event envelope."
  [event]
  (let [missing-keys (set/difference required-keys (set (keys event)))
        blank-keys (filter #(blank? (get event %)) required-keys)]
    (cond-> []
      (seq missing-keys)
      (conj (format "Missing required realtime event keys: %s" (sort missing-keys)))

      (seq blank-keys)
      (conj (format "Realtime event keys cannot be blank: %s" (sort blank-keys)))

      (and (:event-type event) (not (event-types (:event-type event))))
      (conj (format "Unsupported realtime event type [%s]." (:event-type event)))

      (and (:validation-state event) (not (validation-states (:validation-state event))))
      (conj (format "Unsupported realtime validation state [%s]." (:validation-state event)))

      (and (:stream-state event) (not (stream-states (:stream-state event))))
      (conj (format "Unsupported realtime stream state [%s]." (:stream-state event))))))

(defn normalize
  "Normalizes JSON-decoded event values into the keyword enums used internally by CMR."
  [event]
  (reduce
   (fn [event k]
     (cond-> event
       (string? (get event k)) (update k keyword)))
   event
   [:event-type :validation-state :stream-state]))

(defn valid? [event]
  (empty? (validation-errors event)))

(defn envelope
  "Builds a versioned realtime event envelope.

  The payload is expected to carry provider, collection, native-id, event type, stream state,
  validation state, access links, and any provisional metadata needed for immediate discovery."
  [event]
  (assoc (normalize event) :realtime-envelope-version envelope-version))

(defn indexer-message
  "Wraps a realtime envelope as an ingest event for the indexer event handler."
  [event]
  {:action :realtime-granule-event
   :event (envelope event)})

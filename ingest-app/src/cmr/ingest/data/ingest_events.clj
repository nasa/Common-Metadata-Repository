(ns cmr.ingest.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require
   [cmr.ingest.config :as config]
   [cmr.message-queue.services.queue :as queue]))

(defn publish-provider-event
  "Put a provider event on the message queue."
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/provider-exchange-name)]
    (queue/publish-message queue-broker exchange-name msg)))

(defn publish-ingest-event
  "Put an ingest event on the message queue"
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/ingest-exchange-name)]
    (queue/publish-message queue-broker exchange-name msg)))

(defn publish-gran-bulk-update-event
  "Put a granule bulk update event on the message queue"
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/bulk-update-exchange-name)]
    (queue/publish-message queue-broker exchange-name msg)))

(defn trigger-collection-granule-aggregation-cache-refresh
  "Sends a message to trigger a refresh of the collection granule aggregation cache.
   granules-updated-in-last-n indicates a number of seconds back to find granules that were updated.
   If nil then it will find all granules."
  [granules-updated-in-last-n]
  {:action :refresh-collection-granule-aggregation-cache
   :granules-updated-in-last-n granules-updated-in-last-n})

(defn provider-collections-require-reindexing-event
  "Indicates that all the collections within a provider require reindexing. The force-version? attribute
   indicates if during the reindexing we should force elasticsearch to take the version in the database
   regardless of whether its older or not."
  [provider-id force-version?]
  {:action :provider-collection-reindexing
   :provider-id provider-id
   :force-version? force-version?})

(defn provider-autocomplete-suggestion-reindexing-event
  "Create an event representing an autocomplete suggestion reindex command."
  [provider-id]
  {:action :provider-autocomplete-suggestion-reindexing
   :provider-id provider-id})

(defn autocomplete-suggestion-prune-event
  "Create an event representing an autocomplete suggestion prune command."
  []
  {:action :autocomplete-suggestion-prune})

(defn provider-create-event
  "Creates an event representing a provider being created."
  [provider-id]
  {:action :provider-create
   :provider-id provider-id})

(defn provider-update-event
  "Creates an event representing a provider being updated."
  [provider-id]
  {:action :provider-update
   :provider-id provider-id})

(defn provider-delete-event
  "Creates an event representing a provider being deleted."
  [provider-id]
  {:action :provider-delete
   :provider-id provider-id})

(defn collections-bulk-event
  "Creates a collection bulk update event that is for multiple collections."
  [provider-id task-id bulk-update-params user-id]
  {:action :bulk-update
   :provider-id provider-id
   :task-id task-id
   :bulk-update-params bulk-update-params
   :user-id user-id})

(defn ingest-collection-bulk-update-event
  "Creates a single collection bulk update event. This is to update a single collection."
  [provider-id task-id concept-id bulk-update-params user-id]
  {:action :collection-bulk-update
   :provider-id provider-id
   :task-id task-id
   :concept-id concept-id
   :bulk-update-params bulk-update-params
   :user-id user-id})

(defn granules-bulk-event
  "Creates a granule bulk update event that is for multiple granules."
  [provider-id task-id user-id instructions]
  {:action :granules-bulk
   :provider-id provider-id
   :task-id task-id
   :bulk-update-params instructions
   :user-id user-id})

(defn ingest-granule-bulk-update-event
  "Creates an event representing bulk update for a single granule.
   bulk-update-params holds specific info for each update. e.g. event-type, granule-ur, url."
  [provider-id task-id user-id instruction]
  {:action :granule-bulk-update
   :provider-id provider-id
   :task-id task-id
   :bulk-update-params instruction
   :user-id user-id})

(defn granule-bulk-update-task-cleanup-event
 "Create an event representing a granule bulk update cleanup event"
 []
 {:action :granule-bulk-update-task-cleanup})

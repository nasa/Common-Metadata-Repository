(ns cmr.ingest.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require [cmr.ingest.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]))

(defn publish-provider-event
  "Put a provider event on the message queue."
  [context msg]
  (let [queue-broker (get-in context [:system :queue-broker])
        exchange-name (config/provider-exchange-name)]
    (queue/publish-message queue-broker exchange-name msg)))

(defn provider-collections-require-reindexing-event
  "Indicates that all the collections within a provider require reindexing. The force-version? attribute
   indicates if during the reindexing we should force elasticsearch to take the version in the database
   regardless of whether its older or not."
  [provider-id force-version?]
  {:action :provider-collection-reindexing
   :provider-id provider-id
   :force-version? force-version?})

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


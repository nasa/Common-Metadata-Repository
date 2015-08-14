(ns cmr.ingest.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require [cmr.ingest.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]))

(defn publish-event
  "Put an ingest event on the message queue."
  [context msg]
   (let [timeout-ms (config/publish-queue-timeout-ms)
         queue-broker (get-in context [:system :queue-broker])
         exchange-name (config/ingest-exchange-name)]
     (queue/publish-message queue-broker exchange-name msg timeout-ms)))

(defn collection-concept-update-event
  "Creates an event representing a collection concept being updated or created."
  [concept-id revision-id]
  {:action :concept-update
   :concept-id concept-id
   :revision-id revision-id})

(defn granule-concept-update-event
  "Creates an event representing a granule concept being updated or created."
  [coll-concept concept-id revision-id]
  {:action :concept-update
   ;; The entry title is used in the virtual product processing to avoid having to fetch the full
   ;; metadata to determine if this is a granule that requires processing.
   :entry-title (get-in coll-concept [:extra-fields :entry-title])
   :concept-id concept-id
   :revision-id revision-id})

(defn concept-delete-event
  "Creates an event representing a concept being deleted."
  [concept-id revision-id]
  {:action :concept-delete
   :concept-id concept-id
   :revision-id revision-id})

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


(ns cmr.metadata-db.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require [cmr.metadata-db.config :as config]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.message-queue.services.queue :as queue]))

(defn publish-event
  "Put an ingest event on the message queue."
  [context exchange-name msg]
  (when (config/publish-messages)
    (let [timeout-ms (config/publish-timeout-ms)
          queue-broker (get-in context [:system :queue-broker])]
      (queue/publish-message queue-broker exchange-name msg timeout-ms))))

(defmulti concept-update-event
  "Creates an event representing a concept being updated or created."
  (fn [concept]
    (:concept-type concept)))

(defmethod concept-update-event :default
  [{:keys [concept-id revision-id]}]
  {:action :concept-update
   :concept-id concept-id
   :revision-id revision-id})

(defmethod concept-update-event :granule
  [{:keys [concept-id revision-id] :as concept}]
  (let [entry-title (get-in concept [:extra-fields :parent-entry-title])]
    {:entry-title entry-title
     :action :concept-update
     :concept-id concept-id
     :revision-id revision-id}))

(defn concept-delete-event
  "Creates an event representing a concept being deleted."
  [{:keys [concept-id revision-id] :as concept}]
  {:action :concept-delete
   :concept-id concept-id
   :revision-id revision-id})

(defn publish-collection-revision-delete-msg
  "Publishes a message indicating a collection revision was removed."
  [context concept-id revision-id]
  (when (config/publish-messages)
    (let [timeout-ms (config/publish-timeout-ms)
          queue-broker (get-in context [:system :queue-broker])
          exchange-name (config/deleted-collection-revision-exchange-name)
          ;; Note it's important that the format of this message match the ingest event format.
          msg {:action :concept-revision-delete
               :concept-id concept-id
               :revision-id revision-id}]
      (queue/publish-message queue-broker exchange-name msg timeout-ms))))
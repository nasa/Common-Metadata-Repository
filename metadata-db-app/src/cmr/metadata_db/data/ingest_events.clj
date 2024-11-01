(ns cmr.metadata-db.data.ingest-events
  "Allows broadcast of ingest events via the message queue"
  (:require
   [cmr.common.concepts :as cc]
   [cmr.common.services.errors :as errors]
   [cmr.message-queue.services.queue :as queue]
   [cmr.metadata-db.config :as config]))

(defn publish-event
  "Put an ingest event on the message queue."
  [context msg]
  (when-not (:concept-id msg)
    (errors/internal-error! (str "Expecting every message to contain a concept-id. msg: " (pr-str msg))))
  (when (config/publish-messages)
    (when-let [exchange-name-fn (config/concept-type->exchange-name-fn
                                 (cc/concept-id->type (:concept-id msg)))]
      (let [queue-broker (get-in context [:system :queue-broker])]
        (when queue-broker
          (queue/publish-message queue-broker (exchange-name-fn) msg))))))

(defn associations-update-event
  "Create an event representing a list of collections that are associated
  to either a service or tool. This is to re-index the collection when
  a service or tool has been re-indexed and an association exists between the
  two. We don't want to re-index the association, because then the original
  saved concept will be re-indexed n times the number of associations from it,
  and that is not necessary."
  [associations]
  {:action :concept-update
   :concept-id (get-in (first associations) [:extra-fields :associated-concept-id])
   :revision-id (get-in (first associations) [:extra-fields :associated-revision-id])
   :more-concepts (when (> (count associations) 1)
                    (for [association (drop 1 associations)]
                      {:concept-id (get-in association [:extra-fields :associated-concept-id])
                       :revision-id (get-in association [:extra-fields :associated-revision-id])}))})

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
  [{:keys [concept-id revision-id]}]
  {:action :concept-delete
   :concept-id concept-id
   :revision-id revision-id})

(defn concept-expire-event
  "Creates an event representing a concept being expired"
  [{:keys [concept-id revision-id]}]
  {:action :expire-concept
   :concept-id concept-id
   :revision-id revision-id})

(defn publish-concept-revision-delete-msg
  "Publishes a message indicating a concept revision was removed."
  [context concept-id revision-id]
  (when (config/publish-messages)
    (let [queue-broker (get-in context [:system :queue-broker])
          exchange-name (config/deleted-concept-revision-exchange-name)
          ;; Note it's important that the format of this message match the ingest event format.
          msg {:action :concept-revision-delete
               :concept-id concept-id
               :revision-id revision-id}]
      (queue/publish-message queue-broker exchange-name msg))))

(defn publish-tombstone-delete-msg
  "Publishes a message indicating a tombstone was removed/overwritten with updated concept"
  [context concept-id revision-id]
  (when (config/publish-messages)
    (let [queue-broker (get-in context [:system :queue-broker])
          exchange-name (config/deleted-granule-exchange-name)
          ;; Note it's important that the format of this message match the ingest event format.
          msg {:action :tombstone-delete
               :concept-id concept-id
               :revision-id revision-id}]
      (queue/publish-message queue-broker exchange-name msg))))

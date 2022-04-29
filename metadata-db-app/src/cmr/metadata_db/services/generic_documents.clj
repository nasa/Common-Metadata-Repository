(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [cmr.common.concepts :as common-concepts]
   [cmr.common.services.errors :as errors]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.metadata-db.data.generic-documents :as data]))

(defn insert-generic-document
  [context params provider-id document]
  (let [db (mdb-util/context->db context)
        doc_name (:Name document) 
        document (-> document
                     (assoc :provider-id provider-id)
                     (assoc :concept-type :generic)
                     (assoc :revision-id 1))
        concept-id (data/generate-concept-id db document)
        document (-> document
                     (assoc :native-id doc_name)
                     (assoc :concept-id concept-id))
        result (data/save-concept db provider-id document)]

    result))

(defn read-generic-document
  [context params provider-id concept-id]
  (let [db (mdb-util/context->db context)
        concept-type :generic
        doc (data/get-concept db concept-type {:provider-id provider-id} concept-id)]
    doc))

(defn update-generic-document
  [context params provider-id concept-id]
  (let [db (mdb-util/context->db context)
        document (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))
        latest-rev-id (:revision-id document)
        document (-> document
                     (assoc :revision-id (+ latest-rev-id 1)))
        _ (data/save-concept db provider-id document)
        result (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id])]
    result))

(defn delete-generic-document
  [context params provider-id concept-id]
  {})

(comment defn- update-subscription-notification-time-in-database
  "Do the work for updating the subscription notificitation time in the database.
  The record is lazly created, if a subscription exists, but not notification
  record then create the notification, otherwise update the existing one."
 [db subscription-id]
 (if (sub-note/subscription-exists? db subscription-id)
   (if (sub-note/sub-notification-exists? db subscription-id)
     (sub-note/update-sub-notification db subscription-id)
     (sub-note/save-sub-notification db subscription-id))
   (errors/throw-service-error :not-found (msg/subscription-not-found subscription-id))))

(comment defn update-subscription-notification
  "update a subscription notification record, creating one if needed, complain
  if subscription id is not valid or not found"
  [context subscription-id]
  (def context context)
  (let [errors (common-concepts/concept-id-validation subscription-id)
        db (mdb-util/context->db context)]
    (if (nil? errors)
      (update-subscription-notification-time-in-database db subscription-id)
      (errors/throw-service-error
        :not-found
        (msg/subscription-not-found subscription-id)))))

(ns cmr.metadata-db.services.generic-documents
 "Buisness logic for Generic Documents"
  (:require
   [clj-time.coerce :as coerce]
   [cmr.common.concepts :as common-concepts]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as tkeeper]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.metadata-db.data.generic-documents :as data]))

(defn insert-generic-document
  [context params provider-id document]
  (let [db (mdb-util/context->db context)
        document (-> document
                     (assoc :provider-id provider-id)
                     (assoc :concept-type :generic)
                     (assoc :revision-id 1)
                     (assoc :created-at (str (tkeeper/now))) ;; make sure i dont break in-mem side
                     (assoc :revision-date (dtp/clj-time->date-time-str (tkeeper/now)))) ;; make sure i dont break in-mem side
        concept-id (data/generate-concept-id db document)
        document (-> document
                     (assoc :native-id (.toString (java.util.UUID/randomUUID)))
                     (assoc :concept-id concept-id))
        _ (data/save-concept db provider-id document)
        result (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))]
    result))

(defn read-generic-document
  [context params provider-id concept-id]
  (let [db (mdb-util/context->db context)
        concept-type :generic
        doc (data/get-concept db concept-type {:provider-id provider-id} concept-id)]
    doc))

(defn update-generic-document
  [context params provider-id concept-id document]
  (let [db (mdb-util/context->db context)
        latest-document (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))
        latest-rev-id (:revision-id latest-document)
        orig-native-id (:native-id latest-document)
        orig-concept-id (:concept-id latest-document)
        orig-create-date (:created-at latest-document)
        metadata (-> document
                     (assoc :provider-id provider-id)
                     (assoc :concept-type :generic)
                     (assoc :revision-id (+ latest-rev-id 1))
                     (assoc :native-id orig-native-id)
                     (assoc :concept-id orig-concept-id)
                     (assoc :revision-date (dtp/clj-time->date-time-str (tkeeper/now)))
                     (assoc :created-at orig-create-date))
        _ (data/save-concept db provider-id metadata)
        result (first (data/get-latest-concepts db :generic {:provider-id provider-id} [concept-id]))
        ]
    (println result)
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

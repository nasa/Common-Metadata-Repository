(ns cmr.metadata-db.services.sub-notifications
 "Buisness logic for subscription notifications"
  (:require
   [cmr.common.concepts :as common-concepts]
   [cmr.common.services.errors :as errors]
   [cmr.metadata-db.data.oracle.sub-notifications :as sub-note]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.util :as mdb-util]))

(defn- update-subscription-notification-time-in-database
  "Do the work for updating the subscription notificitation time in the database.
  The record is lazly created, if a subscription exists, but not notification
  record then create the notification, otherwise update the existing one."
 [db subscription-id]
 (if (sub-note/subscription-exists? db subscription-id)
   (if (sub-note/sub-notification-exists? db subscription-id)
     (sub-note/update-sub-notification db subscription-id)
     (sub-note/save-sub-notification db subscription-id))
   (errors/throw-service-error :not-found (msg/subscription-not-found subscription-id))))

(defn update-subscription-notification
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

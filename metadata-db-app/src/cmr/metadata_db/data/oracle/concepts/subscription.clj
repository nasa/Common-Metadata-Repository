(ns cmr.metadata-db.data.oracle.concepts.subscription
  "Implements multi-method variations for subscriptions"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as concepts]
   [cmr.metadata-db.data.oracle.sub-notifications :as sub-notifs]
   [cmr.oracle.connection :as oracle]))

(defn add-last-notified-at-if-present
  [subscription db-result db]
  (if-let [last-notified (:last_notified_at db-result)]
    (assoc-in subscription [:extra-fields :last-notified-at]
              (oracle/oracle-timestamp->str-time db last-notified))
    subscription))

(defmethod concepts/db-result->concept-map :subscription
  [concept-type db provider-id result]
  (some-> (concepts/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :subscription)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :normalized-query] (:normalized_query result))
          (assoc-in [:extra-fields :subscription-type] (:subscription_type result))
          (assoc-in [:extra-fields :subscription-name] (:subscription_name result))
          (assoc-in [:extra-fields :subscriber-id] (:subscriber_id result))
          (add-last-notified-at-if-present result db)
          (assoc-in [:extra-fields :collection-concept-id]
                    (:collection_concept_id result))))

(defn- subscription-concept->insert-args
  [concept]
  (let [{{:keys [subscription-name
                 subscriber-id
                 collection-concept-id
                 normalized-query
                 subscription-type]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (concepts/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "subscription_name"
                   "subscriber_id" "collection_concept_id" "normalized_query"
                   "subscription_type"])
     (concat values [provider-id user-id subscription-name
                     subscriber-id collection-concept-id normalized-query
                     subscription-type])]))

(defmethod concepts/concept->insert-args [:subscription false]
  [concept _]
  (subscription-concept->insert-args concept))

(defmethod concepts/concept->insert-args [:subscription true]
  [concept _]
  (subscription-concept->insert-args concept))

(defmethod concepts/after-save :subscription
  [db provider sub]
  (when (:deleted sub)
    ;; Cascade deletion to real deletes of subscription notifications. The intended functionality
    ;; since the subscription_notification row is purged is that, if this subscription were
    ;; re-ingested (and un-tombstoned), we just look back 24 hours, as if the sub were brand new.
    (sub-notifs/delete-sub-notification db (:concept-id sub))))

(ns cmr.metadata-db.data.oracle.concepts.subscription
  "Implements multi-method variations for subscriptions"
  (:require [cmr.metadata-db.data.oracle.concepts :as concepts]))

(defmethod concepts/db-result->concept-map :subscription
  [concept-type db provider-id result]
  (some-> (concepts/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :subscription)
          (assoc :user-id (:user_id result))
          (assoc :description (:description result))
          (assoc :email-address (:email_address result))
          (assoc :collection-concept-id (:collection_concept_id result))))

;; Only "CMR" provider is supported which is not considered a 'small' provider.
(defmethod concepts/concept->insert-args [:subscription false]
  [concept _]
  (let [{user-id :user-id
         description :description
         email-address :email-address
         collection-concept-id :collection-concept-id} concept
        [cols values] (concepts/concept->common-insert-args concept)]
    [(concat cols ["user_id" "description" "email_address" "collection_concept_id"])
     (concat values [user-id description email-address collection-concept-id])]))

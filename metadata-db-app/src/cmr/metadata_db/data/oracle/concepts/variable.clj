(ns cmr.metadata-db.data.oracle.concepts.variable
  "Implements multi-method variations for variables"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :variable
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :variable-name] (:variable_name result))
          (assoc-in [:extra-fields :measurement] (:measurement result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider.
;; If we ever associate real providers with variables then we will need to add support
;; for small providers as well.
(defmethod c/concept->insert-args [:variable false]
  [concept _]
  (let [{{:keys [variable-name measurement]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "variable_name" "measurement"])
     (concat values [provider-id user-id variable-name measurement])]))

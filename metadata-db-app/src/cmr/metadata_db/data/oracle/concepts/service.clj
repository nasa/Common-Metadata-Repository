(ns cmr.metadata-db.data.oracle.concepts.service
  "Implements multi-method variations for services."
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :service
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :service)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :service-name] (:service_name result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider.
;; If we ever associate real providers with services then we will need to add support
;; for small providers as well.
(defmethod c/concept->insert-args [:service false]
  [concept _]
  (let [{{:keys [service-name]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "service_name"])
     (concat values [provider-id user-id service-name])]))

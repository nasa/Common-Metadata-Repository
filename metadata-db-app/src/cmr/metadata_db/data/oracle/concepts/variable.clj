(ns cmr.metadata-db.data.oracle.concepts.variable
  "Implements multi-method variations for variables."
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :variable
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :variable-name] (:variable_name result))
          (assoc-in [:extra-fields :measurement] (:measurement result))
          (assoc-in [:extra-fields :fingerprint] (:fingerprint result))))

(defn- variable-concept->insert-args
  [concept]
  (let [{{:keys [variable-name measurement fingerprint]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "variable_name" "measurement" "fingerprint"])
     (concat values [provider-id user-id variable-name measurement fingerprint])]))

(defmethod c/concept->insert-args [:variable false]
  [concept _]
  (variable-concept->insert-args concept))

(defmethod c/concept->insert-args [:variable true]
  [concept _]
  (variable-concept->insert-args concept))

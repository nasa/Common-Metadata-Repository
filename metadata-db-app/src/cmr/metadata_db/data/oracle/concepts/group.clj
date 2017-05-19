(ns cmr.metadata-db.data.oracle.concepts.group
  "Implements multi-method variations for groups"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :access-group
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :access-group)
          (assoc :user-id (:user_id result))))

(defn group-concept->insert-args
  "Returns common insert args plus user_id and provider_id."
  [concept]
  (let [{:keys [user-id provider-id]} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["user_id" "provider_id"])
     (concat values [user-id provider-id])]))

(defmethod c/concept->insert-args [:access-group false]
  [concept _]
  (group-concept->insert-args concept))

(defmethod c/concept->insert-args [:access-group true]
  [concept _]
  (group-concept->insert-args concept))

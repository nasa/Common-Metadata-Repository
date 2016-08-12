(ns cmr.metadata-db.data.oracle.concepts.humanizer
  "Implements multi-method variations for humanizers"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :humanizer
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :humanizer)
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider.
;; If we ever associate real providers with humanizers then we will need to add support
;; for small providers as well.
(defmethod c/concept->insert-args [:humanizer false]
  [concept _]
  (let [{user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["user_id"])
     (concat values [user-id])]))

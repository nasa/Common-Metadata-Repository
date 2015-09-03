(ns cmr.metadata-db.data.oracle.concepts.tag
  "Implements multi-method variations for tags"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.date-time-parser :as p]
            [clj-time.coerce :as cr]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.data.concepts :as concepts]))

(defmethod c/db-result->concept-map :tag
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tag)
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider. If we
;; ever associate real providers with tags then we will need to add support for small providers
;; as well.
(defmethod c/concept->insert-args [:tag false]
  [concept _]
  (let [{user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["user_id"])
     (concat values [user-id])]))

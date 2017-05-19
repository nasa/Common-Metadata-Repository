(ns cmr.metadata-db.data.oracle.concepts.variable-association
  "Implements multi-method variations for variables"
  (:require
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concept-tables :as tables]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.oracle.connection :as oracle]))

(defmethod c/db-result->concept-map :variable-association
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable-association)
          (assoc-in [:extra-fields :associated-concept-id] (:associated_concept_id result))
          (assoc-in [:extra-fields :associated-revision-id]
                    (when-let [ari (:associated_revision_id result)]
                      (long ari)))
          (assoc-in [:extra-fields :variable-name] (:variable_name result))
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider. If we
;; ever associate real providers with variable-associatons then we will need to add support
;; for small providers as well.
(defmethod c/concept->insert-args [:variable-association false]
  [concept _]
  (let [{{:keys [associated-concept-id associated-revision-id variable-name]} :extra-fields
         :keys [user-id]} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["associated_concept_id" "associated_revision_id" "variable_name" "user_id"])
     (concat values [associated-concept-id associated-revision-id variable-name user-id])]))

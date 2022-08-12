(ns cmr.metadata-db.data.oracle.concepts.tag-association
  "Implements multi-method variations for tag associations"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :tag-association
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tag-association)
          (assoc-in [:extra-fields :associated-concept-id] (:associated_concept_id result))
          (assoc-in [:extra-fields :associated-revision-id]
                    (when-let [ari (:associated_revision_id result)]
                      (long ari)))
          (assoc-in [:extra-fields :tag-key] (:source_concept_identifier result))
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider. If we
;; ever associate real providers with tag-associatons then we will need to add support for small providers
;; as well.
(defmethod c/concept->insert-args [:tag-association false]
  [concept _]
  (let [{{:keys [associated-concept-id associated-revision-id tag-key]} :extra-fields
         :keys [user-id]} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["associated_concept_id" "associated_revision_id" "source_concept_identifier" "user_id" "association_type"])
     (concat values [associated-concept-id associated-revision-id tag-key user-id "TAG-COLLECTION"])]))

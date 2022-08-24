(ns cmr.metadata-db.data.oracle.concepts.tool-association
  "Implements multi-method variations for tools"
  (:require
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :tool-association
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tool-association)
          (assoc-in [:extra-fields :associated-concept-id] (:associated_concept_id result))
          (assoc-in [:extra-fields :associated-revision-id]
                    (when-let [ari (:associated_revision_id result)]
                      (long ari)))
          (assoc-in [:extra-fields :tool-concept-id] (:source_concept_identifier result))
          (assoc :user-id (:user_id result))))

(defn- var-assoc-concept->insert-args
  [concept]
  (let [{{:keys [associated-concept-id associated-revision-id tool-concept-id]} :extra-fields
         :keys [user-id]} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["associated_concept_id" "associated_revision_id" "source_concept_identifier" "user_id" "association_type"])
     (concat values [associated-concept-id associated-revision-id tool-concept-id user-id "TOOL-COLLECTION"])]))

(defmethod c/concept->insert-args [:tool-association false]
  [concept _]
  (var-assoc-concept->insert-args concept))

(defmethod c/concept->insert-args [:tool-association true]
  [concept _]
  (var-assoc-concept->insert-args concept))

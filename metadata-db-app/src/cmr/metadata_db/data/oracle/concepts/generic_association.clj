(ns cmr.metadata-db.data.oracle.concepts.generic-association
  "Implements multi-method variations for generic association concepts"
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :generic-association
  [concept-type db provider-id result]
  (let [concept-id-in-result (:concept_id result)
        concept-type-in-result (when concept-id-in-result
                                 (concepts/concept-id->type concept-id-in-result))]
    ;; For now, not every association in CMR_ASSOCIATIONS table has :generic-association type.
    ;; there are also :variable-association, :service-association and :tool-association.
    ;; We are working towards making them all :generic-association if possible. 
    (if (and concept-type-in-result
             (not=  concept-type concept-type-in-result))
      (c/db-result->concept-map concept-type-in-result db provider-id result)
      (some-> (c/db-result->concept-map :default db provider-id result)
              (assoc :concept-type :generic-association)
              (assoc-in [:extra-fields :associated-concept-id] (:associated_concept_id result))
              (assoc-in [:extra-fields :associated-revision-id]
                        (when-let [ari (:associated_revision_id result)]
                          (long ari)))
              (assoc-in [:extra-fields :source-concept-identifier] (:source_concept_identifier result))
              (assoc-in [:extra-fields :source-revision-id]
                        (when-let [sri (:source_revision_id result)]
                          (long sri)))
              (assoc :user-id (:user_id result))))))

(defn- generic-assoc-concept->insert-args
  [concept]
  (let [{{:keys [associated-concept-id associated-revision-id source-concept-identifier source-revision-id]} :extra-fields
         :keys [user-id]} concept
        [cols values] (c/concept->common-insert-args concept)
        source-concept-type-str (name (concepts/concept-id->type source-concept-identifier))
        dest-concept-type-str (name (concepts/concept-id->type associated-concept-id))
        association-type (string/upper-case (str source-concept-type-str "-" dest-concept-type-str))]
    (if source-revision-id
      [(concat cols ["associated_concept_id" "associated_revision_id" "source_concept_identifier" "source_revision_id" "user_id" "association_type"])
       (concat values [associated-concept-id associated-revision-id source-concept-identifier source-revision-id user-id association-type])]
      [(concat cols ["associated_concept_id" "associated_revision_id" "source_concept_identifier" "user_id" "association_type"])
       (concat values [associated-concept-id associated-revision-id source-concept-identifier user-id association-type])])))

(defmethod c/concept->insert-args [:generic-association false]
  [concept _]
  (generic-assoc-concept->insert-args concept))

(defmethod c/concept->insert-args [:generic-association true]
  [concept _]
  (generic-assoc-concept->insert-args concept))

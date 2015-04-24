(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.common.services.errors :as errors]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.util :as util]))

(defn unique-field-constraint
  "Returns a function which verifies that there is only one non-deleted collection for a provider
  with the value for the given field."
  [field]
  (fn [db concept]
    (let [field-value (get-in concept [:extra-fields field])]
      (let [concepts (->> (c/find-latest-concepts db {:concept-type (:concept-type concept)
                                                      :provider-id (:provider-id concept)
                                                      field field-value})
                          ;; Remove tombstones from the list of concepts
                          (remove :deleted))
            num-concepts (count concepts)]
        (cond
          (zero? num-concepts)
          (errors/internal-error!
            (format "Unable to find saved concept for provider [%s] and %s [%s]"
                    (:provider-id concept)
                    (name field)
                    field-value))
          (> num-concepts 1)
          [(msg/duplicate-field-msg
             field
             (remove #(= (:concept-id concept) (get % :concept-id)) concepts))])))))


(def concept-type->constraints
  "Maps concept type to a list of constraint functions to run."
  {:collection [(unique-field-constraint :entry-title)
                (unique-field-constraint :entry-id)]
   :granule [(unique-field-constraint :granule-ur)]})

(defn perform-post-commit-constraint-checks
  "Perform the post commit constraint checks aggregating any constraint violations. Returns nil if
  there are no constraint violations. Otherwise it performs any necessary database cleanup using
  the provided rollback-function and throws a :conflict error."
  [db concept rollback-function]
  (let [constraints (concept-type->constraints (:concept-type concept))]
    (when-let [errors (seq (util/apply-validations constraints db concept))]
      (rollback-function)
      (errors/throw-service-errors :conflict errors))))

(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.common.services.errors :as errors]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.util :as util]))

(defn entry-title-unique-constraint
  "Verifies that there is only one valid collection with the entry-title that matches the
  entry-title of the provided concept."
  [db concept]
  (let [entry-title (get-in concept [:extra-fields :entry-title])]
    (let [concepts (->> (c/find-latest-concepts db {:concept-type :collection
                                                    :provider-id (:provider-id concept)
                                                    :entry-title entry-title})
                        ;; Remove tombstones from the list of concepts
                        (remove :deleted))
          num-concepts (count concepts)]
      (cond
        (zero? num-concepts)
        (errors/internal-error!
          (str "Unable to find saved concept for provider [" (:provider-id concept)
               "] and entry-title [" entry-title "]"))
        (> num-concepts 1)
        [(msg/duplicate-entry-titles concepts)]))))

(def concept-type->constraints
  "Maps concept type to a list of constraint functions to run."
  {:collection [entry-title-unique-constraint]})

(defn perform-post-commit-constraint-checks
  "Perform the post commit constraint checks aggregating any constraint violations. Returns nil if
  there are no constraint violations. Otherwise it performs any necessary database cleanup using
  the provided rollback-function and throws a :conflict error."
  [db concept rollback-function]
  (let [constraints (concept-type->constraints (:concept-type concept))]
    (when-let [errors (seq (util/apply-validations constraints db concept))]
      (rollback-function)
      (errors/throw-service-errors :conflict errors))))

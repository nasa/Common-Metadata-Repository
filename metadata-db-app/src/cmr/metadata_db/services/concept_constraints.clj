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

(defmulti post-commit-constraint-checks
  "Perform the post commit constraint checks. Return a list of constraint violations if any."
  (fn [concept]
    (:concept-type concept)))

(defmethod post-commit-constraint-checks :collection
  [concept]
  (util/compose-validations [entry-title-unique-constraint]))

(defmethod post-commit-constraint-checks :granule
  [concept]
  (util/compose-validations []))

(defn perform-post-commit-constraint-checks
  "Perform the post commit constraint checks. Return a list of constraint violations if any.
  Performs any necessary database cleanup using the provided rollback-function"
  [concept rollback-function]
  (util/build-validator
    :conflict
    (fn [& args]
      (when-let [errors (seq (apply (post-commit-constraint-checks concept) args))]
        (rollback-function)
        errors))))

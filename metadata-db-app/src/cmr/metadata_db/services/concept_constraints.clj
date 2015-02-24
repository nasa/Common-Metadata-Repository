(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.common.services.errors :as errors]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as msg]))

(defn entry-title-unique-constraint
  "Verifies that there is only one valid collection with the entry-title which matches the
  entry-title of the provided concept."
  [db concept]
  (when-let [entry-title (get-in concept [:extra-fields :entry-title])]
    (let [concepts (->>
                     (c/find-latest-concepts db {:concept-type :collection
                                                 :provider-id (:provider-id concept)
                                                 :entry-title entry-title})
                     (filter (complement :deleted)))
          num-concepts (count concepts)]
      (cond
        (zero? num-concepts)
        (errors/internal-error!
          (str "Unable to find saved concept by entry-title [" entry-title "]"))
        (> num-concepts 1)
        (msg/duplicate-entry-titles concepts)))))

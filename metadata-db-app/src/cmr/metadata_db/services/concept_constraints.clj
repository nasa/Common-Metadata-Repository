(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.concept-service :as cs]
            [clojure.string :as str]))

(defn- get-max-revision-id
  "Get max revisison ID from a vector of concept maps."
  [concepts]
  (apply max (map :revision-id concepts)))

(defn- keep-max-revision-id
  "Remove all but the most recent revision id from a vector of concept maps."
  [concepts]
  (let [max-revision-id (get-max-revision-id concepts)]
    (filter #(= max-revision-id (:revision-id %)) concepts)))

(comment
  (def concepts-found
    [{:concept-id "C1" :revision-id 1 :entry-title "E1" :deleted false}
     {:concept-id "C1" :revision-id 2 :entry-title "E1" :deleted false}
     {:concept-id "C2" :revision-id 1 :entry-title "E1" :deleted false}
     {:concept-id "C2" :revision-id 2 :entry-title "E1" :deleted true}
     {:concept-id "C3" :revision-id 1 :entry-title "E1" :deleted false}])

  (keep-latest-non-deleted-concepts concepts-found)
  )

(defn keep-latest-non-deleted-concepts
  "Helper function used to filter out all but the latest revision ID for a concept ID. Filters out
  tombstones as well. Takes a vector of concepts."
  [concepts]
  (->> concepts
       (group-by :concept-id)
       (map val)
       (mapcat keep-max-revision-id)
       (filter (complement :deleted))))

(defn entry-title-unique-constraint
  "Verifies that there is only one valid collection with the entry-title which matches the
  entry-title of the provided concept."
  [db concept]
  (let [concepts (->>
                   (cs/find-concepts db {:concept-type :collection
                                         :provider-id (:provider-id concept)
                                         :entry-title (:entry-title concept)})

                   keep-latest-non-deleted-concepts)
        num-concepts (count concepts)]
    (when (not= 1 num-concepts)
      (if (> 1 num-concepts)
        (format "Entry-title must be unique. Found [%d] concepts with the same entry-title. The concept-ids are [%s]."
                num-concepts
                (str/join ", " (map :concept-id concepts)))
        ;; May want to throw a service error here instead of return the string message
        "Unexpected error. Concept appears to not have saved correctly."))))

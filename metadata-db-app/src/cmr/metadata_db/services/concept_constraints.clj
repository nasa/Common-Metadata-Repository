(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.metadata-db.services.messages :as msg]
            [cmr.common.concepts :as cc]
            [clojure.set :as set]
            [cmr.common.services.errors :as errors]
            [cmr.common.date-time-parser :as p]
            [cmr.common.util :as util]
            [cmr.metadata-db.services.concept-service :as cs]))

(defn- max-revision-id
  "Get max revisison ID from a vector of concept maps."
  [concepts]
  (apply max (map :revision-id concepts)))

(defn- remove-old-revision-ids
  "Remove all but the most recent revision id from a vector of concept maps."
  [concepts]
  (filter #(= (max-revision-id concepts) (:revision-id %)) concepts))

(comment
  (def concepts-found
    [{:concept-id "C1" :revision-id 1 :entry-title "E1" :deleted 0}
     {:concept-id "C1" :revision-id 2 :entry-title "E1" :deleted 0}
     {:concept-id "C2" :revision-id 1 :entry-title "E1" :deleted 0}
     {:concept-id "C2" :revision-id 2 :entry-title "E1" :deleted 1}
     {:concept-id "C3" :revision-id 1 :entry-title "E1" :deleted 0}])
  )

(defn remove-old-revisions-and-tombstones
  "Helper function used to filter out all but the latest revision ID for a concept ID.  Filters out
  tombstones as well.  Takes a vector of concepts."
  [concepts]
  (->> concepts
       (group-by :concept-id)
       (map val)
       (map remove-old-revision-ids)
       flatten
       (filter #(= 0 (:deleted %)))))

(defn entry-title-unique-constraint
  "Verifies that there is only one valid collection with the "
  [db concept]
  (let [concepts (cs/find-concepts db {:concept-type :collection
                                       :provider-id (:provider-id concept)
                                       :entry-title (:entry-title concept)})
        num-concepts (remove-old-revisions-and-tombstones concepts)]
    (if (= 1 num-concepts)
      nil
      (str "Entry-title must be unique.  Found " num-concepts "concepts with the same entry-title"))))
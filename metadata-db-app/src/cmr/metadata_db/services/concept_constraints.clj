(ns cmr.metadata-db.services.concept-constraints
  "Functions for enforcing constraint checks just after a concept has been saved."
  (:require [cmr.common.services.errors :as errors]
            [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.messages :as msg]
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
  ;; TODO these example concepts are wrong. The entry title is in extra-fields map
  (def concepts-found
    [{:concept-id "C1" :revision-id 1 :entry-title "E1" :deleted false}
     {:concept-id "C1" :revision-id 2 :entry-title "E1" :deleted false}
     {:concept-id "C2" :revision-id 1 :entry-title "E1" :deleted false}
     {:concept-id "C2" :revision-id 2 :entry-title "E1" :deleted true}
     {:concept-id "C3" :revision-id 1 :entry-title "E1" :deleted false}])

  (keep-latest-non-deleted-concepts concepts-found)
  )

;; TODO I don't think we need this anymore. I forgot we have a function to find the latest concepts
;; in the data.concepts namespace.
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
  (let [entry-title (get-in concept [:extra-fields :entry-title])
        concepts (->>
                   (c/find-latest-concepts db {:concept-type :collection
                                               :provider-id (:provider-id concept)
                                               :entry-title entry-title})

                   (filter (complement :deleted)))
        num-concepts (count concepts)]
    (cmr.common.dev.capture-reveal/capture-all)
    (cond
      (zero? num-concepts)
      (errors/internal-error!
        (str "Was not able to find saved concept by entry-title [" entry-title "]"))

      (> num-concepts 1)
      (msg/duplicate-entry-titles concepts))))

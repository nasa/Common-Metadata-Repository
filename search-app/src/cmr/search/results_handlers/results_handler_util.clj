(ns cmr.search.results-handlers.results-handler-util
  "Provides useful functions for several of the result handlers."
  (:require
   [clojure.walk :as walk]
   [cmr.common.util :as util]))

(defn replace-snake-keys
  "Replace specified keys in the nested association data structure with
  snake_case version"
  [associations]
  (->> associations
    (walk/postwalk-replace {:concept-id :concept_id})
    (walk/postwalk-replace {:revision-id :revision_id})))

(defn build-each-concept-association-list
  "This function builds a map with the passed in conept-key as the key and
  a list of concept ids from the passed in associations."
  [concept-key associations]
  {concept-key (seq (util/remove-nils-empty-maps-seqs
                     (mapv :concept-id associations)))})

(defn build-association-concept-id-list
  "Builds the association list from the passed in associations."
  [associations _for-concept-type]
  (util/remove-nils-empty-maps-seqs
   (into {}
         (map #(build-each-concept-association-list % (get associations %))
              (keys associations)))))

(defn main-detail-assoc-structure
  "Build the main association details structure."
  [concept-key associations]
  {concept-key (map #(if (string? %) {:concept-id %} %) associations)})

(defn build-association-details
  "Builds the association details from the passed in associations
  The associations passed in are in the following structure:
  {<plural concept key1> [{:concept-id <concept-id1> :revision-id <revision-id1> :data <data1>}
                          {:concept-id <concept-id2> :revision-id <revision-id2> :data <data2>}]
   <plural concept key2> [<concept-id3> <concept-id4>]}"
  [associations _for-concept-type]
  (util/remove-nils-empty-maps-seqs
   (into {}
         (map #(main-detail-assoc-structure % (get associations %))
              (keys associations)))))

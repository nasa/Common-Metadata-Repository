(ns cmr.search.results-handlers.results-handler-util
  "Provides useful functions for several of the result handlers."
  (:require
   [cmr.common.util :as util]))

(defn build-each-concept-association-list
  "This function builds a map with the passed in conept-key as the key and
  a list of concept ids from the passed in associations."
  [concept-key associations]
  ;;All the associations, generic or service/tool/variable associations,
  ;;have been converted in indexer-app/src/cmr/indexer/data/concepts/association_util.clj
  ;;to only contain concept-id and revision-id.
  {concept-key (seq (util/remove-nils-empty-maps-seqs
                     (mapv :concept-id associations)))})

(defn build-association-concept-id-list
  "Builds the association list from the passed in associations."
  [associations _for-concept-type]
  (util/remove-nils-empty-maps-seqs
   (into {}
         (map #(build-each-concept-association-list % (get associations %))
              (keys associations)))))

(defn build-association-details
  "Builds the association details from the passed in associations. 
  The associations passed in are in the following structure:
  {<plural concept key1> [{:concept-id <concept-id1> :revision-id <revision-id1> :data <data1>}
                         {:concept-id <concept-id> :revision-id <revision-id2> :data <data2>}]}"
  [associations _for-concept-type]
  (util/remove-nils-empty-maps-seqs associations))

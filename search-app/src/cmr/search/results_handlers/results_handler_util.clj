(ns cmr.search.results-handlers.results-handler-util
  "Provides useful functions for several of the result handlers."
  (:require
   [clojure.set :as s]
   [cmr.common.util :as util]))

(defn build-each-concept-association-list
  "This function builds a map with the passed in conept-key as the key and
  a list of concept ids from the passed in associations."
  [concept-key associations for-concept-type]
  (if (= :collection for-concept-type)
    {concept-key (or (seq (util/remove-nils-empty-maps-seqs
                           (mapv :variable-concept-id associations)))
                     (seq (util/remove-nils-empty-maps-seqs
                           (mapv :service-concept-id associations)))
                     (seq (util/remove-nils-empty-maps-seqs
                           (mapv :tool-concept-id associations)))
                     (seq (util/remove-nils-empty-maps-seqs
                           (mapv :concept-id associations))))}
    {concept-key (or (seq (util/remove-nils-empty-maps-seqs
                           (mapv :associated-concept-id associations)))
                     (seq (util/remove-nils-empty-maps-seqs
                           (mapv :concept-id associations))))}))

(defn build-association-concept-id-list
  "Builds the association list from the passed in associations."
  [associations for-concept-type]
  (util/remove-nils-empty-maps-seqs
   (into {}
         (map #(build-each-concept-association-list % (get associations %) for-concept-type)
              (keys associations)))))

(defn build-detail-assoc-struct
  "Builds the detailed association structure."
  [association for-concept-type]
  (if (string? association)
    {:concept-id association}
    (if (= :collection for-concept-type)
      (-> association
          (s/rename-keys {:variable-concept-id :concept-id
                          :service-concept-id :concept-id
                          :tool-concept-id :concept-id
                          :source-revision-id :revision-id})
          (dissoc :associated-concept-id :associated-revision-id)
          (util/remove-nil-keys)) 
      (-> association
          (s/rename-keys {:associated-concept-id :concept-id
                          :associated-revision-id :revision-id})
          (dissoc :variable-concept-id :service-concept-id :tool-concept-id :source-revision-id)
          (util/remove-nil-keys)))))

(defn main-detail-assoc-structure
  "Build the main association detail structure."
  [concept-key associations for-concept-type]
  {concept-key (map #(build-detail-assoc-struct % for-concept-type) associations)})

(defn build-association-details
  "Builds the association details from the passed in associations for a specific 
  concept type. The associations passed in are in the following structure:
  {<plural concept key> [{<(variable|service|tool) concept-id> <associated concept -id> <data>}]}"
  [associations for-concept-type]
  (util/remove-nils-empty-maps-seqs
   (into {}
         (map #(main-detail-assoc-structure % (get associations %) for-concept-type)
              (keys associations)))))

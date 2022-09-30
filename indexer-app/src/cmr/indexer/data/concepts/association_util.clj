(ns cmr.indexer.data.concepts.association-util
  "Contains functions to parse and convert generic associations to a map structure 
  for each generic concept type."
  (:require
   [cmr.common.concepts :as cc]))

(defn allow-group-by-concept-type
  "Creates a temporary structure so that we can group the concept ids by concept type."
  [concept-id]
  {:concept-type (cc/concept-id->type concept-id)
   :concept-id concept-id})

(defn build-single-concept-association-list
  "Creates a map of a pluralized concept key with the a group of like concept, concept ids.
  The return is used for a concepts association list."
  [concept-key associations]
  {(cc/pluralize-concept-type concept-key) (mapv :concept-id associations)})

(defn generic-assocs->concept-named-assoc-list
  "Builds a consolidated map for all of the generic associations where the actual
  concept-type name gets used.  This map will be used in the concepts association list."
  [concept-associations-group]
  (let [concept-keys (keys concept-associations-group)]
    (into {} (map #(build-single-concept-association-list % (get concept-associations-group %)) concept-keys))))

(defn generic-assoc-list->assoc-struct
  "Takes an association concept id list, 
  creates a temporary structure to group concept id list,
  Then creates a plural concept named list of like concept ids. example:
  Takes [\"DQS1200000012-PROV1\" \"OO1200000014-PROV1\"]
   and converts it to 
   {:dataqualitysummaries [\"DQS1200000012-PROV1\"]
    :orderoptions [\"OO1200000014-PROV1\"]}"
  [generic-assoc-list]
  (generic-assocs->concept-named-assoc-list
   (group-by :concept-type (map #(allow-group-by-concept-type %) generic-assoc-list))))

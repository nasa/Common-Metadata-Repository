(ns cmr.search.services.collection-concept-id-extractor
  "Defines protocols and functions to extract collection concept ids from the query constructs"
  (:require [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]))

(defprotocol ExtractCollectionConceptId
  "Defines a function to extract collection concept ids into a vector."
  (extract-collection-concept-ids
    [c]
    "Extract collection concept ids into a vector."))

(extend-protocol ExtractCollectionConceptId
  cmr.search.models.query.Query
  (extract-collection-concept-ids
    [query]
    (extract-collection-concept-ids (:condition query)))

  cmr.search.models.query.ConditionGroup
  (extract-collection-concept-ids
    [{:keys [conditions]}]
    (mapcat extract-collection-concept-ids conditions))

  cmr.search.models.query.NegatedCondition
  (extract-collection-concept-ids
    [{:keys [condition]}]
    (if-not (empty? (extract-collection-concept-ids condition))
      (errors/internal-error! "collection-concept-id should not be allowed in NegatedCondition.")))

  cmr.search.models.query.StringCondition
  (extract-collection-concept-ids
    [{:keys [field value]}]
    (if (= :collection-concept-id field)
      [value]
      []))

  cmr.search.models.query.StringsCondition
  (extract-collection-concept-ids
    [{:keys [field values]}]
    (if (= :collection-concept-id field)
      values
      []))

  ;; catch all extractor
  java.lang.Object
  (extract-collection-concept-ids [this] []))
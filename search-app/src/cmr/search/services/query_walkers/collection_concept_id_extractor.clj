(ns cmr.search.services.query-walkers.collection-concept-id-extractor
  "Defines protocols and functions to extract collection concept ids from the query constructs"
  (:require [cmr.common.services.errors :as errors]
            [cmr.elastic-utils.es-query-model :as cqm]
            [cmr.search.models.query :as qm]))

(defprotocol ExtractCollectionConceptId
  "Defines a function to extract collection concept ids"
  (extract-collection-concept-ids
    [c]
    "Extract collection concept ids and returns them as a set. If there are none to return or they
    do not definitively apply to the whole query then an empty set will returned"))

(extend-protocol ExtractCollectionConceptId
  cmr.elastic-utils.es-query-model.Query
  (extract-collection-concept-ids
    [query]
    ;; This is expected to the entry way into the
    (let [concept-ids (set (extract-collection-concept-ids (:condition query)))]
      (if (or (concept-ids :none) (concept-ids :any))
        #{}
        concept-ids)))

  cmr.elastic-utils.es-query-model.ConditionGroup
  (extract-collection-concept-ids
    [{:keys [operation conditions]}]
    (let [concept-ids (set (mapcat extract-collection-concept-ids conditions))]
      (cond
        (concept-ids :none) [:none]
        (and (= operation :or) (concept-ids :any)) [:any]
        :else
        (disj concept-ids :any))))


  cmr.elastic-utils.es-query-model.StringCondition
  (extract-collection-concept-ids
    [{:keys [field value pattern]}]
    (if pattern
      [:any]
      (if (= field :collection-concept-id)
        [value]
        [:any])))

  cmr.elastic-utils.es-query-model.StringsCondition
  (extract-collection-concept-ids
    [{:keys [field values]}]
    (if (= field :collection-concept-id)
      values
      [:any]))

  cmr.search.models.query.CollectionQueryCondition
  (extract-collection-concept-ids
    [_]
    (errors/internal-error! "extract-collection-concept-ids does not support CollectionQueryCondition"))

  ;; catch all extractor
  java.lang.Object
  (extract-collection-concept-ids
    [this]
    [:any]))


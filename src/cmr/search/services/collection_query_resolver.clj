(ns cmr.search.services.collection-query-resolver
  "Defines protocols and functions to resolve collection query conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.services.parameters :as p]))

(defprotocol ResolveCollectionQuery
  "Defines a function to resolve a collection query condition into conditions of collection-concept-ids."
  (resolve-collection-query
    [c context]
    "Converts a collection query condition into conditions of collection-concept-ids."))

(extend-protocol ResolveCollectionQuery
  cmr.search.models.query.Query
  (resolve-collection-query
    [query context]
    (qm/map->Query {:concept-type (:concept-type query)
                    :condition (resolve-collection-query (:condition query) context)}))

  cmr.search.models.query.ConditionGroup
  (resolve-collection-query
    [{:keys [operation conditions]} context]
    (let [resolved-conditions (map #(resolve-collection-query % context) conditions)]
      (qm/->ConditionGroup operation resolved-conditions)))

  cmr.search.models.query.CollectionQueryCondition
  (resolve-collection-query
    [condition context]
    (let [result (idx/execute-query context (qm/->Query :collection (:condition condition)))
          {:keys [references]} result
          collection-concept-ids (map :concept-id references)]
      (if (empty? collection-concept-ids)
        (qm/->MatchNoneCondition)
        (qm/or-conds
          (map #(p/parameter->condition :granule :collection_concept_id % {}) collection-concept-ids)))))

  ;; catch all resolver
  java.lang.Object
  (resolve-collection-query [this context] this))


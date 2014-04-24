(ns cmr.search.services.collection-query-resolver
  "Defines protocols and functions to resolve collection query conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.common.services.errors :as e]))

(defprotocol ResolveCollectionQuery
  "Defines a function to resolve a collection query condition into conditions of collection-concept-ids."
  (resolve-collection-query
    [c context]
    "Converts a collection query condition into conditions of collection-concept-ids."))

(extend-protocol ResolveCollectionQuery
  cmr.search.models.query.Query
  (resolve-collection-query
    [query context]
    (qm/map->Query {
                    :page-size (:page-size query)
                    :page-num (:page-num query)
                    :concept-type (:concept-type query)
                    :condition (resolve-collection-query (:condition query) context)}))

  cmr.search.models.query.ConditionGroup
  (resolve-collection-query
    [{:keys [operation conditions]} context]
    (let [resolved-conditions (map #(resolve-collection-query % context) conditions)]
      (qm/->ConditionGroup operation resolved-conditions)))

  cmr.search.models.query.CollectionQueryCondition
  (resolve-collection-query
    [{:keys [condition]} context]
    (let [result (idx/execute-query context
                                    (qm/query {:concept-type :collection :condition condition :page-size :unlimited}))
          {:keys [hits references]} result
          collection-concept-ids (map :concept-id references)]
      ;; we require ALL the collections
      (when (> hits (count references))
        (e/internal-error! "Failed to retreive all collections - need to increase :unlimited size."))
      (if (empty? collection-concept-ids)
        (qm/->MatchNoneCondition)
        (qm/or-conds
          (map #(qm/string-condition :collection_concept_id %) collection-concept-ids)))))

  ;; catch all resolver
  java.lang.Object
  (resolve-collection-query [this context] this))
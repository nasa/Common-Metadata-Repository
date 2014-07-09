(ns cmr.search.services.collection-query-resolver
  "Defines protocols and functions to resolve collection query conditions"
  (:require [cmr.search.models.query :as qm]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.elastic-search-index :as idx])
  (:import cmr.search.models.query.CollectionQueryCondition))


(defprotocol ResolveCollectionQuery
  "Defines a function to resolve a collection query condition into conditions of collection-concept-ids."
  (merge-collection-queries
    [c]
    "Merges together collection query conditions to reduce the number of collection queries.")

  (resolve-collection-query
    [c context]
    "Converts a collection query condition into conditions of collection-concept-ids."))

(extend-protocol ResolveCollectionQuery
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.Query
  (merge-collection-queries
    [query]
    (update-in query [:condition] merge-collection-queries))

  (resolve-collection-query
    [query context]
    (-> query
        merge-collection-queries
        (update-in [:condition] #(resolve-collection-query % context))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.ConditionGroup
  (merge-collection-queries
    [{:keys [operation conditions]}]
    ;; This is where the real merging happens. Collection queries at the same level in an AND or OR
    ;; can be merged together.
    (let [conditions (map merge-collection-queries conditions)
          {coll-q-conds true others false} (group-by #(= (type %) CollectionQueryCondition) conditions)]
      (if (seq coll-q-conds)
        (qm/group-conds
          operation
          (concat [(qm/->CollectionQueryCondition
                     (qm/group-conds operation (map :condition coll-q-conds)))]
                  others))
        (qm/group-conds operation others))))


  (resolve-collection-query
    [{:keys [operation conditions]} context]
    (let [resolved-conditions (map #(resolve-collection-query % context) conditions)]
      (qm/group-conds operation resolved-conditions)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.CollectionQueryCondition
  (merge-collection-queries [this] this)

  (resolve-collection-query
    [{:keys [condition]} context]
    (let [result (idx/execute-query context
                                    (qm/query {:concept-type :collection
                                               :condition condition
                                               :page-size :unlimited}))
          collection-concept-ids (map :_id (get-in result [:hits :hits]))]
      (if (empty? collection-concept-ids)
        (qm/->MatchNoneCondition)
        (qm/or-conds
          (map #(qm/string-condition :collection-concept-id % true false) collection-concept-ids)))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (merge-collection-queries [this] this)
  (resolve-collection-query [this context] this))


(ns cmr.common-app.services.search.related-item-resolver
  "Finds RelatedItemQueryConditions in a query, executes them, processes the results and replaces
  them with the retrieved condition"
  (:require [cmr.common-app.services.search.query-model :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.complex-to-simple :as c2s]
            [cmr.common-app.services.search.elastic-search-index :as idx]))

(defprotocol ResolveRelatedItemQueryCondition
  (resolve-related-item-conditions
    [c context]
    "Finds and executes RelatedItemQueryConditions and replaces them with resulting conditions"))

(extend-protocol ResolveRelatedItemQueryCondition
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.Query

  (resolve-related-item-conditions
    [query context]
    (update-in query [:condition] #(resolve-related-item-conditions % context)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.ConditionGroup

  (resolve-related-item-conditions
    [condition context]
    (update-in condition [:conditions] (partial mapv #(resolve-related-item-conditions % context))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.NegatedCondition

  (resolve-related-item-conditions
    [condition context]
    (update-in condition [:condition] #(resolve-related-item-conditions % context)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common_app.services.search.query_model.RelatedItemQueryCondition

  (resolve-related-item-conditions
    [{:keys [concept-type condition result-fields results-to-condition-fn]} context]
    (->> (qm/query {:concept-type concept-type
                    :condition condition
                    :page-size :unlimited
                    :result-format :query-specified
                    :result-fields result-fields})
         (c2s/reduce-query context)
         (idx/execute-query context)
         results-to-condition-fn))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (resolve-related-item-conditions [this context] this))

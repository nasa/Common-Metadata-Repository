(ns cmr.elastic-utils.search.es-related-item-resolver
  "Finds RelatedItemQueryConditions in a query, executes them, processes the
   results and replaces them with the retrieved condition"
  (:require
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-index :as idx]
   [cmr.elastic-utils.search.query-transform :as c2s])
  #_{:clj-kondo/ignore [:unused-import]}
  (:import cmr.common.services.search.query_model.Query
           cmr.common.services.search.query_model.ConditionGroup
           cmr.common.services.search.query_model.NegatedCondition
           cmr.common.services.search.query_model.RelatedItemQueryCondition))

(defprotocol ResolveRelatedItemQueryCondition
  (resolve-related-item-conditions
    [c context]
    "Finds and executes RelatedItemQueryConditions and replaces them with resulting conditions"))

(extend-protocol ResolveRelatedItemQueryCondition
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common.services.search.query_model.Query

  (resolve-related-item-conditions
    [query context]
    (update-in query [:condition] #(resolve-related-item-conditions % context)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common.services.search.query_model.ConditionGroup

  (resolve-related-item-conditions
    [condition context]
    (update-in condition [:conditions] (partial mapv #(resolve-related-item-conditions % context))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common.services.search.query_model.NegatedCondition

  (resolve-related-item-conditions
    [condition context]
    (update-in condition [:condition] #(resolve-related-item-conditions % context)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.common.services.search.query_model.RelatedItemQueryCondition

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
  (resolve-related-item-conditions [this _context] this))

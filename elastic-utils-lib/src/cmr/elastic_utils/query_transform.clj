(ns cmr.elastic-utils.query-transform)

(defprotocol ComplexQueryToSimple
  "Defines a function to convert a complex / high level query/condition into simpler ones that
  can be optimized more easily."
  (reduce-query-condition
    [query context]
    "Converts a high-level query condition into a simpler form."))

(defn reduce-query
  "Converts a complex / high level query/condition into simpler ones that
  can be optimized more easily."
  [context query]
  (reduce-query-condition query context))

(extend-protocol ComplexQueryToSimple
  cmr.elastic-utils.es-query-model.Query
  (reduce-query-condition
    [query context]
    (update-in query [:condition] reduce-query-condition context))

  cmr.elastic-utils.es-query-model.ConditionGroup
  (reduce-query-condition
    [condition context]
    (update-in condition [:conditions] (fn [conditions]
                                         (map #(reduce-query-condition % context) conditions))))

  cmr.elastic-utils.es-query-model.NegatedCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] reduce-query-condition context))

  ;; catch all
  java.lang.Object
  (reduce-query-condition
    [this context]
    ;; do nothing
    this))

(ns cmr.search.data.complex-to-simple
  (:require [cmr.search.models.query :as qm]))

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
  cmr.search.models.query.Query
  (reduce-query-condition
    [query context]
    (update-in query [:condition] reduce-query-condition context))

  cmr.search.models.query.ConditionGroup
  (reduce-query-condition
    [condition context]
    (update-in condition [:conditions] (fn [conditions]
                                         (map #(reduce-query-condition % context) conditions))))


  cmr.search.models.query.CollectionQueryCondition
  (reduce-query-condition
    [condition context]
    (update-in condition [:condition] reduce-query-condition context))

  ;; catch all
  java.lang.Object
  (reduce-query-condition
    [this context]
    ;; do nothing
    this))

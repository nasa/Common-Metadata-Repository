(ns cmr.search.data.complex-to-simple
  (:require [cmr.search.models.query :as qm]))

(defprotocol ComplexQueryToSimple
  "Defines a function to convert a complex / high level query/condition into simpler ones that
  can be optimized more easily."
  (reduce-query
    [query context]
    "Converts a high-level query condition into a simpler form."))


(extend-protocol ComplexQueryToSimple
  cmr.search.models.query.Query
  (reduce-query
    [query context]
    (update-in query [:condition] reduce-query context))

  cmr.search.models.query.ConditionGroup
  (reduce-query
    [condition context]
    (update-in condition [:conditions] (fn [conditions]
                                         (map #(reduce-query % context) conditions))))


  cmr.search.models.query.CollectionQueryCondition
  (reduce-query
    [condition context]
    (update-in condition [:condition] reduce-query context))

  ;; catch all
  java.lang.Object
  (reduce-query
    [this context]
    ;; do nothing
    this))

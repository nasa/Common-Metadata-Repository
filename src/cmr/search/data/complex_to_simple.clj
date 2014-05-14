(ns cmr.search.data.complex-to-simple
  (:require [cmr.search.models.query :as qm]))

(defprotocol ComplexQueryToSimple
  "Defines a function to convert a complex / high level query/condition into simpler ones that
  can be optimized more easily."
  (reduce-query
    [query]
    "Converts a high-level query condition into a simpler form."))


(extend-protocol ComplexQueryToSimple
  cmr.search.models.query.Query
  (reduce-query
    [query]
    (update-in query [:condition] reduce-query))

  cmr.search.models.query.ConditionGroup
  (reduce-query
    [condition]
    (update-in condition [:conditions] (partial map reduce-query)))


  cmr.search.models.query.CollectionQueryCondition
  (reduce-query
    [condition]
    (update-in condition [:condition] reduce-query))

  ;; catch all
  java.lang.Object
  (reduce-query
    [this]
    ;; do nothing
    this))

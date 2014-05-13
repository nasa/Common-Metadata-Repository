(ns cmr.search.data.complex-to-simple
  (:require [cmr.search.models.query :as qm]))

(defprotocol ComplexQueryToSimple
  "Defines a function to convert a complex / high level query/condition into simpler ones that
  can be optimized more easily."
  (simplify-query
    [query]
    "Converts a high-level query condition into a simpler form."))


(extend-protocol ComplexQueryToSimple
  cmr.search.models.query.Query
  (simplify-query
    [query]
    (update-in query [:condition] #(simplify-query %)))

  cmr.search.models.query.ConditionGroup
  (simplify-query
    [condition]
    (update-in condition [:conditions] (fn [cond]
                                         (map #(simplify-query %) cond))))


  ;; catch all
  java.lang.Object
  (simplify-query
    [this]
    ;; do nothing
    this))

(ns cmr.common-app.services.search.query-order-by-expense
  "Sorts query conditions within a query by their execution expense. Elasticsearch recommend putting
  cached filters before uncached filters. Expensive queries like exact spatial intersection should
  be placed last."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common-app.services.search.query-model :as qm])
  (:import [cmr.common_app.services.search.query_model
            Query
            ScriptCondition
            ConditionGroup
            NegatedCondition
            NestedCondition
            DateValueCondition
            DateRangeCondition
            NumericValueCondition
            NumericRangeCondition
            NumericRangeIntersectionCondition]))


(defprotocol QueryOrderByExpense
  "Defines functions for ordering queries by expense and returning the expense of a query."
  (order-conditions
    [query-component]
    "Orders the conditions in the query by their expense")
  (expense
    [query-component]
    "Returns the weight of the query component as an integer."))

(extend-protocol QueryOrderByExpense
  ;; Query components that hold other conditions
  Query
  (order-conditions
    [query]
    (update-in query [:condition] order-conditions))
  (expense
    [query]
    (expense (:condition query)))

  ConditionGroup
  (order-conditions
    [group]
    (update-in group [:conditions] #(sort-by expense (map order-conditions %))))
  (expense
    [group]
    ;; A groups weight is determined by the maximum weight of a condition in the group.
    (apply max (map expense (:conditions group))))

  NestedCondition
  (order-conditions
    [c]
    (update-in c [:condition] order-conditions))
  (expense
    [c]
    (expense (:condition c)))

  NegatedCondition
  (order-conditions
    [c]
    (update-in c [:condition] order-conditions))
  (expense
    [c]
    (expense (:condition c)))

  ScriptCondition
  (order-conditions [c] c)
  (expense [c] 100)

  ;; Numeric type conditions

  DateValueCondition
  (order-conditions [c] c)
  (expense [c] 10)

  DateRangeCondition
  (order-conditions [c] c)
  (expense [c] 10)

  NumericValueCondition
  (order-conditions [c] c)
  (expense [c] 10)

  NumericRangeCondition
  (order-conditions [c] c)
  (expense [c] 10)

  NumericRangeIntersectionCondition
  (order-conditions [c] c)
  (expense [c] 10)

  java.lang.Object
  (order-conditions [o] o)
  (expense [o] 1))

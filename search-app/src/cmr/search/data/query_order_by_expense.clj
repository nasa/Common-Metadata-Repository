(ns cmr.search.data.query-order-by-expense
  "Sorts query conditions within a query by their execution expense. Elasticsearch recommend putting
  cached filters before uncached filters. Expensive queries like exact spatial intersection should
  be placed last."
  (:require [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm])
  (:import [cmr.search.models.query
            Query
            ConditionGroup
            NegatedCondition
            NestedCondition
            CollectionQueryCondition
            SpatialCondition
            SpatialCondition
            DateValueCondition
            DateRangeCondition
            NumericValueCondition
            NumericRangeCondition
            NumericRangeIntersectionCondition
            TemporalCondition
            OrbitNumberValueCondition
            OrbitNumberRangeCondition
            CoordinateValueCondition
            CoordinateRangeCondition
            TwoDCoordinateCondition
            TwoDCoordinateSystemCondition
            EquatorCrossingDateCondition
            ScriptCondition]))


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

  CollectionQueryCondition
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

  ;; Spatial and script conditions

  SpatialCondition
  (order-conditions [c] c)
  (expense [c] 100)

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

  TemporalCondition
  (order-conditions [c] c)
  (expense [c] 10)

  OrbitNumberValueCondition
  (order-conditions [c] c)
  (expense [c] 10)

  OrbitNumberRangeCondition
  (order-conditions [c] c)
  (expense [c] 10)

  CoordinateValueCondition
  (order-conditions [c] c)
  (expense [c] 10)

  CoordinateRangeCondition
  (order-conditions [c] c)
  (expense [c] 10)

  TwoDCoordinateCondition
  (order-conditions [c] c)
  (expense [c] 10)

  TwoDCoordinateSystemCondition
  (order-conditions [c] c)
  (expense [c] 10)

  EquatorCrossingDateCondition
  (order-conditions [c] c)
  (expense [c] 10)

  java.lang.Object
  (order-conditions [o] o)
  (expense [o] 1))
(ns cmr.search.data.query-order-by-expense
  "Sorts query conditions within a query by their execution expense. Elasticsearch recommend putting
  cached filters before uncached filters. Expensive queries like exact spatial intersection should
  be placed last."
  (:require [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.query-order-by-expense :as qobe])
  (:import [cmr.search.models.query
            CollectionQueryCondition
            SpatialCondition
            TemporalCondition
            OrbitNumberValueCondition
            OrbitNumberRangeCondition
            CoordinateValueCondition
            CoordinateRangeCondition
            TwoDCoordinateCondition
            TwoDCoordinateSystemCondition
            EquatorCrossingDateCondition]))

(extend-protocol qobe/QueryOrderByExpense

  CollectionQueryCondition
  (order-conditions
   [c]
   (update-in c [:condition] qobe/order-conditions))
  (expense
   [c]
   (qobe/expense (:condition c)))

  SpatialCondition
  (order-conditions [c] c)
  (expense [c] 100)

  ;; Numeric type conditions

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
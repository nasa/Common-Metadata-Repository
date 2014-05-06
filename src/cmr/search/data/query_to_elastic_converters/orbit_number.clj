(ns cmr.search.data.query-to-elastic-converters.orbit-number
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.models.query :as qm]))



(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.OrbitNumberValueCondition
  (condition->elastic
    [condition concept-type]
    (let [orbit-number (:orbit-number condition)
          term-condition (qm/map->NumericValueCondition {:field :orbit-number :value orbit-number})
          start-range-cond (qm/map->NumericRangeCondition {:field :start-orbit-number
                                                           :max-value orbit-number})
          stop-range-cond (qm/map->NumericRangeCondition {:field :stop-orbit-number
                                                          :min-value orbit-number})
          and-clause (qm/and-conds [start-range-cond stop-range-cond])
          or-clause (qm/or-conds [term-condition and-clause])]
      {:nested {:path "orbit-calculated-spatial-domains"
                :filter (q2e/condition->elastic or-clause concept-type)}}))


  cmr.search.models.query.OrbitNumberRangeCondition
  (condition->elastic
    [condition concept-type]
    (let [{:keys [min-value max-value]} condition
          start-orbit-number-range-cond (qm/map->NumericRangeCondition {:field :start-orbit-number
                                                                        :min-value min-value
                                                                        :max-value max-value})
          orbit-number-range-cond (qm/map->NumericRangeCondition {:field :orbit-number
                                                                  :min-value min-value
                                                                  :max-value max-value})
          stop-orbit-number-range-cond (qm/map->NumericRangeCondition {:field :stop-orbit-number
                                                                       :min-value min-value
                                                                       :max-value max-value})
          min-inside-start-cond (qm/map->NumericRangeCondition {:field :start-orbit-number
                                                                :max-value min-value})
          min-inside-stop-cond (qm/map->NumericRangeCondition {:field :stop-orbit-number
                                                               :min-value min-value})
          min-and-clause (qm/and-conds [min-inside-start-cond min-inside-stop-cond])
          max-inside-start-cond (qm/map->NumericRangeCondition {:field :start-orbit-number
                                                                :max-value max-value})
          max-inside-stop-cond (qm/map->NumericRangeCondition {:field :stop-orbit-number
                                                               :min-value max-value})
          max-and-clause (qm/and-conds [max-inside-start-cond max-inside-stop-cond])
          or-clause (qm/or-conds [start-orbit-number-range-cond
                                  orbit-number-range-cond
                                  stop-orbit-number-range-cond
                                  min-and-clause
                                  max-and-clause])]
      {:nested {:path "orbit-calculated-spatial-domains"
                :filter (q2e/condition->elastic or-clause concept-type)}})))
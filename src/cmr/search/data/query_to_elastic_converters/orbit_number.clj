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
    (let [orbit-number (:orbit-number condition)]
      {:nested {:path "orbit-calculated-spatial-domains"
                :filter {:or [{:term { :orbit-number orbit-number}}
                              {:and [{:range {:start-orbit-number {:lte orbit-number}
                                              :execution "fielddata"}}
                                     {:range {:stop-orbit-number {:gte orbit-number}
                                              :execution "fielddata"}}]}]}}}))

  cmr.search.models.query.OrbitNumberRangeCondition
  (condition->elastic
    [condition concept-type]
    (let [{:keys [start-orbit-number-range-condition
                  orbit-number-range-condition
                  stop-orbit-number-range-condition]} condition
          {:keys [min-value max-value]} orbit-number-range-condition]
      {:nested {:path "orbit-calculated-spatial-domains"
                :filter {:or [(q2e/condition->elastic start-orbit-number-range-condition
                                                      concept-type)
                              (q2e/condition->elastic orbit-number-range-condition
                                                      concept-type)
                              (q2e/condition->elastic stop-orbit-number-range-condition
                                                      concept-type)
                              {:and [{:range {:start-orbit-number {:lte min-value}
                                              :execution "fielddata"}}
                                     {:range {:stop-orbit-number {:gte max-value}
                                              :execution "fielddata"}}]}]}}})))

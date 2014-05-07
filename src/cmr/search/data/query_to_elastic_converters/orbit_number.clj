(ns cmr.search.data.query-to-elastic-converters.orbit-number
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.models.query :as qm]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.messages :as m]))



(defn- orbit-number-range-condition-both
  "Creates a grouped condition from an OrbitNumberRangeCondition with both min-value and max.'"
  [min-value max-value]
  (let [start-orbit-number-range-cond (qm/numeric-range :start-orbit-number min-value max-value)
        orbit-number-range-cond (qm/numeric-range :orbit-number min-value max-value)
        stop-orbit-number-range-cond (qm/numeric-range :stop-orbit-number min-value max-value)
        min-inside-start-cond (qm/numeric-range :start-orbit-number nil min-value)
        min-inside-stop-cond (qm/numeric-range :stop-orbit-number min-value nil)
        min-and-clause (qm/and-conds [min-inside-start-cond min-inside-stop-cond])
        max-inside-start-cond (qm/numeric-range :start-orbit-number nil max-value)
        max-inside-stop-cond (qm/numeric-range :stop-orbit-number max-value nil)
        max-and-clause (qm/and-conds [max-inside-start-cond max-inside-stop-cond])]
    (qm/or-conds [start-orbit-number-range-cond
                  orbit-number-range-cond
                  stop-orbit-number-range-cond
                  min-and-clause
                  max-and-clause])))

(defn- orbit-number-range-condition-min
  "Creates a grouped condition with just the min-value specified."
  [min-value]
  (let [stop-orbit-number-range-cond (qm/numeric-range :stop-orbit-number min-value nil)
        orbit-number-range-cond (qm/numeric-range :orbit-number min-value nil)]
    (qm/or-conds [stop-orbit-number-range-cond orbit-number-range-cond])))


(defn- orbit-number-range-condition-max
  "Creates a grouped condition with just the max specified."
  [max-value]
  (let [start-orbit-number-range-cond (qm/numeric-range :start-orbit-number nil max-value)
        orbit-number-range-cond (qm/numeric-range :orbit-number nil max-value)]
    (qm/or-conds [start-orbit-number-range-cond orbit-number-range-cond])))

(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.OrbitNumberValueCondition
  (condition->elastic
    [condition concept-type]
    (let [orbit-number (:orbit-number condition)
          term-condition (qm/map->NumericValueCondition {:field :orbit-number :value orbit-number})
          start-range-cond (qm/numeric-range :start-orbit-number nil orbit-number)
          stop-range-cond (qm/numeric-range :stop-orbit-number orbit-number nil)
          and-clause (qm/and-conds [start-range-cond stop-range-cond])
          or-clause (qm/or-conds [term-condition and-clause])
          nested-condition (qm/nested-condition :orbit-calculated-spatial-domains or-clause)]
      (q2e/condition->elastic nested-condition concept-type)))


  cmr.search.models.query.OrbitNumberRangeCondition
  (condition->elastic
    [condition concept-type]
    (let [{:keys [min-value max-value]} condition
          group-condtion (cond
                           (and min-value max-value)
                           (orbit-number-range-condition-both min-value max-value)

                           min-value
                           (orbit-number-range-condition-min min-value)

                           max-value
                           (orbit-number-range-condition-max max-value)

                           :else
                           (errors/internal-error! (m/nil-min-max-msg)))
          nested-condition (qm/nested-condition :orbit-calculated-spatial-domains group-condtion)]
      (q2e/condition->elastic nested-condition concept-type))))

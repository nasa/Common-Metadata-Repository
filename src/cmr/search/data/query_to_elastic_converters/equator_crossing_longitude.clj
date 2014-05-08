(ns cmr.search.data.query-to-elastic-converters.equator-crossing-longitude
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.models.query :as qm]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.messages :as m]))



(defn- equator-crossing-longitude-condition-both
  "Creates a grouped condition from an EquatorCrossingLongitudeCondition with both min-value and max.'"
  [min-value max-value]
  (if (>= max-value min-value)
    (qm/numeric-range :equator-crossing-longitude min-value max-value)

    ;; If the lower bound is higher than the upper bound then we need to construct two ranges
    ;; to allow us to cross the 180/-180 boundary)
    (let [lower-query (qm/numeric-range :equator-crossing-longitude min-value 180.0)
          upper-query (qm/numeric-range :equator-crossing-longitude -180.0 max-value)]
      (qm/or-conds [lower-query upper-query]))))

(defn- equator-crossing-longitude-condition-min
  "Creates a grouped condition with just the min-value specified."
  [min-value]
  (qm/numeric-range :equator-crossing-longitude min-value nil))

(defn- equator-crossing-longitude-condition-max
  "Creates a grouped condition with just the max specified."
  [max-value]
  (qm/numeric-range :equator-crossing-longitude nil max-value))

(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.EquatorCrossingLongitudeCondition
  (condition->elastic
    [condition concept-type]
    (let [{:keys [min-value max-value]} condition
          group-condtion (cond
                           (and min-value max-value)
                           (equator-crossing-longitude-condition-both min-value max-value)

                           min-value
                           (equator-crossing-longitude-condition-min min-value)

                           max-value
                           (equator-crossing-longitude-condition-max max-value)

                           :else
                           (errors/internal-error! (m/nil-min-max-msg)))
          nested-condition (qm/nested-condition :orbit-calculated-spatial-domains group-condtion)]
      (q2e/condition->elastic nested-condition concept-type))))

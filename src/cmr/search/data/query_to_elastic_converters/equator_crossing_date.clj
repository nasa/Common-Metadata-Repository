(ns cmr.search.data.query-to-elastic-converters.equator-crossing-date
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [cmr.search.data.query-to-elastic :as q2e]
            [cmr.search.models.query :as qm]
            [cmr.common.services.errors :as errors]
            [cmr.search.data.messages :as m]))


(extend-protocol q2e/ConditionToElastic
  cmr.search.models.query.EquatorCrossingDateCondition
  (condition->elastic
    [condition concept-type]
    (let [{:keys [start-date end-date]} condition
          range-cond (qm/date-range-condition :equator-crossing-date-time start-date end-date)
          nested-condition (qm/nested-condition :orbit-calculated-spatial-domains range-cond)]
      (q2e/condition->elastic nested-condition concept-type))))

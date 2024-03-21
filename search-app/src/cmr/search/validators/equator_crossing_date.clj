(ns cmr.search.validators.equator-crossing-date
  "Contains functions for validating equator-crossing-date conditions"
  (:require [cmr.elastic-utils.es-query-model :as qm]
            [cmr.elastic-utils.es-query-validation :as v]))


(extend-protocol v/Validator
  cmr.search.models.query.EquatorCrossingDateCondition
  (validate
    [{:keys [start-date end-date]}]
    (let [date-range-condition (qm/map->DateRangeCondition {:start-date start-date
                                                            :end-date end-date})]
      (v/validate date-range-condition))))
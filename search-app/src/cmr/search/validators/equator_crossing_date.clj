(ns cmr.search.validators.equator-crossing-date
  "Contains functions for validating equator-crossing-date conditions"
  (:require
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-query-validation :as val]))

(extend-protocol val/Validator
  cmr.search.models.query.EquatorCrossingDateCondition
  (validate
    [{:keys [start-date end-date]}]
    (let [date-range-condition (qm/map->DateRangeCondition {:start-date start-date
                                                            :end-date end-date})]
      (val/validate date-range-condition))))

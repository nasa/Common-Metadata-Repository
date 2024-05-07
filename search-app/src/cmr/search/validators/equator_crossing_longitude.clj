(ns cmr.search.validators.equator-crossing-longitude
  "Contains functions for validating equator-crossing-longitude conditions"
  (:require
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-query-validation :as val]
   [cmr.elastic-utils.validators.numeric-range :as nm]
   [cmr.search.services.messages.orbit-number-messages :as onm]))

(extend-protocol val/Validator
  cmr.search.models.query.EquatorCrossingLongitudeValueCondition
  (validate
    [{:keys [value]}]
    (when (or (> value 180.0)
           (< value -180.0))
      (onm/non-numeric-equator-crossing-longitude-parameter))))

(extend-protocol val/Validator
  cmr.search.models.query.EquatorCrossingLongitudeRangeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (nm/min-max-not-both-nil numeric-range-condition))))

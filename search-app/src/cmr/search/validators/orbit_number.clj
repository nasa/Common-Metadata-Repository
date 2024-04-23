(ns cmr.search.validators.orbit-number
  "Contains functions for validating orbit-number conditions"
  (:require
   [clojure.set]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-query-validation :as val]))

(extend-protocol val/Validator
  cmr.search.models.query.OrbitNumberRangeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (val/validate numeric-range-condition))))

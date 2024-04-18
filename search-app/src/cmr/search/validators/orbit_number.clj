(ns cmr.search.validators.orbit-number
  "Contains functions for validating orbit-number conditions"
  (:require [clojure.set]
            [cmr.common.services.search.query-model :as qm]
            [cmr.elastic-utils.es-query-validation :as v]))


(extend-protocol v/Validator
  cmr.search.models.query.OrbitNumberRangeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (v/validate numeric-range-condition))))

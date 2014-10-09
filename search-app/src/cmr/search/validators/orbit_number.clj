(ns cmr.search.validators.orbit-number
  "Contains functions for validating orbit-number conditions"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.search.validators.validation :as v]))


(extend-protocol v/Validator
  cmr.search.models.query.OrbitNumberRangeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (v/validate numeric-range-condition))))
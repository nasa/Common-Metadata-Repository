(ns cmr.search.validators.equator-crossing-longitude
  "Contains functions for validating equator-crossing-longitude conditions"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.services.messages.orbit-number-messages :as on-msg]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.messages :as m]))


(extend-protocol v/Validator
  cmr.search.models.query.EquatorCrossingLongitudeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (let [errors (v/validate numeric-range-condition)]
        ;; remove the min/max error if present since it does not apply for this parameter
        (remove #(= % (m/min-value-greater-than-max min-value max-value)) errors)))))
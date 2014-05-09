(ns cmr.search.validators.equator-crossing-longitude
  "Contains functions for validating equator-crossing-longitude conditions"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.numeric-range :as nm]))


(extend-protocol v/Validator
  cmr.search.models.query.EquatorCrossingLongitudeCondition
  (validate
    [{:keys [min-value max-value]}]
    (let [numeric-range-condition (qm/map->NumericRangeCondition {:min-value min-value
                                                                  :max-value max-value})]
      (nm/min-max-not-both-nil numeric-range-condition))))
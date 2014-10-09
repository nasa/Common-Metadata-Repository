(ns cmr.search.validators.equator-crossing-date
  "Contains functions for validating equator-crossing-date conditions"
  (:require [clojure.set]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]
            [cmr.search.validators.date-range :as dr]))


(extend-protocol v/Validator
  cmr.search.models.query.EquatorCrossingDateCondition
  (validate
    [{:keys [start-date end-date]}]
    (let [date-range-condition (qm/map->DateRangeCondition {:start-date start-date
                                                            :end-date end-date})]
      (v/validate date-range-condition))))
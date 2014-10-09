(ns cmr.search.validators.temporal
  "Contains functions for validating temporal condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]))

(extend-protocol v/Validator
  cmr.search.models.query.TemporalCondition
  (validate
    [temporal]
    ;; Reused the date range condition validators
    (v/validate (qm/map->DateRangeCondition {:field :temporal
                                             :start-date (:start-date temporal)
                                             :end-date (:end-date temporal)}))))

(ns cmr.search.validators.temporal
  "Contains functions for validating temporal condition"
  (:require [cmr.elastic-utils.es-query-model :as qm]
            [cmr.elastic-utils.es-query-validation :as v]))

(extend-protocol v/Validator
  cmr.search.models.query.TemporalCondition
  (validate
    [temporal]
    ;; Reused the date range condition validators
    (v/validate (qm/map->DateRangeCondition {:field :temporal
                                             :start-date (:start-date temporal)
                                             :end-date (:end-date temporal)}))))

(ns cmr.search.validators.temporal
  "Contains functions for validating temporal condition"
  (:require
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-query-validation :as val]))

(extend-protocol val/Validator
  cmr.search.models.query.TemporalCondition
  (validate
    [temporal]
    ;; Reused the date range condition validators
    (val/validate (qm/map->DateRangeCondition {:field :temporal
                                             :start-date (:start-date temporal)
                                             :end-date (:end-date temporal)}))))

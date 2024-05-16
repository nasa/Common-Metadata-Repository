(ns cmr.elastic-utils.validators.numeric-range
  "Contains functions for validating numeric range condition"
  (:require
   [cmr.elastic-utils.search.es-messenger :as es-msg]
   [cmr.elastic-utils.search.es-query-validation :as q-val])
  #_{:clj-kondo/ignore [:unused-import]}
  (:import cmr.common.services.search.query_model.NumericRangeCondition))

(defn min-is-lte-max
  "Validates min is less than or equal to max."
  [{:keys [min-value max-value]}]
  (if (and min-value max-value (> min-value max-value))
    [(es-msg/min-value-greater-than-max min-value max-value)]
    []))

(defn min-max-not-both-nil
  "Validates that at least one of min/max are not nil."
  [{:keys [min-value max-value]}]
  (if (or min-value max-value)
    []
    [(es-msg/nil-min-max-msg)]))

(extend-protocol q-val/Validator
  cmr.common.services.search.query_model.NumericRangeCondition
  (validate
    [numeric-range]
    (concat (min-max-not-both-nil numeric-range) (min-is-lte-max numeric-range))))

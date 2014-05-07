(ns cmr.search.validators.numeric-range
  "Contains functions for validating numeric range condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]
            [cmr.search.data.datetime-helper :as h]
            [cmr.search.validators.messages :as m]
            [cmr.search.data.messages :as sdm]))

(defn- min-is-lte-max
  "Validates min is less than or equal to max."
  [{:keys [min-value max-value]}]
  (if (and min-value max-value (> min-value max-value))
    [(m/min-value-greater-than-max min-value max-value)]
    []))

(defn- min-max-not-both-nil
  "Validates that at least one of min/max are not nil."
  [{:keys [min-value max-value]}]
  (if (or min-value max-value)
    []
    [(sdm/nil-min-max-msg)]))

(extend-protocol v/Validator
  cmr.search.models.query.NumericRangeCondition
  (validate
    [numeric-range]
    (concat (min-max-not-both-nil numeric-range) (min-is-lte-max numeric-range))))

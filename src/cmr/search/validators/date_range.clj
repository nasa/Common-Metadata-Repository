(ns cmr.search.validators.date-range
  "Contains functions for validating date range condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]
            [cmr.search.data.datetime-helper :as h]))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (try
    (let [{:keys [start-date end-date]} date-range]
      (if (and start-date end-date (t/after? start-date end-date))
        [(format "start_date [%s] must be before end_date [%s]" (h/datetime->string start-date) (h/datetime->string end-date))]
        []))
    (catch IllegalArgumentException e
      [(format "temporal date is invalid: %s" e)])))

(extend-protocol v/Validator
  cmr.search.models.query.DateRangeCondition
  (validate
    [date-range]
    (start-date-is-before-end-date date-range)))

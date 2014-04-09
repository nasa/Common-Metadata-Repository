(ns cmr.search.validators.date-range
  "Contains functions for validating date range condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]))

(defn- string->datetime
  "Convert the given string (in format like 2014-04-05T18:45:51Z) to Joda datetime.
  Returns nil for nil string, throws IllegalArgumentException for mal-formatted string.
  This is more strict than the string->datetime function in terms of format validation."
  [s]
  (when s (f/parse (f/formatters :date-time-no-ms) s)))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (try
    (let [{:keys [start-date end-date]} date-range
          start (string->datetime start-date)
          end (string->datetime end-date)]
      (if (and start end (t/after? start end))
        [(format "start_date [%s] must be before end_date [%s]"start-date end-date)]
        []))
    (catch IllegalArgumentException e
      [(format "temporal date is invalid: %s" e)])))

(extend-protocol v/Validator
  cmr.search.models.query.DateRangeCondition
  (validate
    [date-range]
    (start-date-is-before-end-date date-range)))

(ns cmr.search.validators.date-range
  "Contains functions for validating date range condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- string->datetime
  "Convert the given string (in format like 2014-04-05T18:45:51Z) to datetime.
  Returns nil for nil string, throws IllegalArgumentException for mal-formatted string"
  [s]
  (when s (f/parse (f/formatters :date-time-no-ms) s)))

(defn- start-date-is-before-end-date
  "Validates start-date is before end-date"
  [date-range]
  (try
    (let [{:keys [start-date end-date]} date-range
          start (string->datetime start-date)
          end (string->datetime end-date)]
      (when (and start end (t/after? start end))
        [(format "start_date [%s] must be before end_date [%s]"
                 start-date end-date)]))
    (catch IllegalArgumentException e
      [(format "temporal date is invalid: %s" e)])))

(def date-range-validations
  "A list of the functions that validates some aspect of a date-range condition.
  They all accept date-range as an argument and return a list of errors."
  [start-date-is-before-end-date])

(defn validate
  "Validate the given date-range condition, returns a sequence of errors if validation fails"
  [date-range]
  (seq (reduce (fn [errors validation]
                 (concat errors (validation date-range)))
               []
               date-range-validations)))

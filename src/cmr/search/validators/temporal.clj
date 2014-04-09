(ns cmr.search.validators.temporal
  "Contains functions for validating temporal condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.validators.validation :as v]))

;; map of temporal field to parameter name in the api document
(def printable-name {:start-day "temporal_start_day"
                     :end-day "temporal_end_day"})

(defn- day-valid?
  "Validates if the given day in temporal is an integer between 1 and 366 inclusive"
  [day temporal]
  (when-let [day-value (day temporal)]
    (try
      (let [num (Integer/parseInt day-value)]
        (when (or (< num 1) (> num 366))
          [(format "%s [%s] must be an integer between 1 and 366" (printable-name day) day-value)]))
      (catch NumberFormatException e
        [(format "%s [%s] must be an integer between 1 and 366" (printable-name day) day-value)]))))

(defn- temporal-days-are-valid
  "Validates temporal start-day and end-day"
  [temporal]
  (concat (day-valid? :start-day temporal)
          (day-valid? :end-day temporal)))

(defn- start-day-must-be-with-start-date
  "Validates that start-day must be accompanied by a start-date"
  [temporal]
  (let [{:keys [start-day]} temporal
        {:keys [start-date]} (:date-range-condition temporal)]
    (when (and start-day (nil? start-date))
      ["temporal_start_day must be accompanied by a temporal_start"])))

(defn- end-day-must-be-with-end-date
  "Validates that end-day must be accompanied by a end-date"
  [temporal]
  (let [{:keys [end-day]} temporal
        {:keys [end-date]} (:date-range-condition temporal)]
    (when (and end-day (nil? end-date))
      ["temporal_end_day must be accompanied by a temporal_end"])))

(def temporal-validations
  "A list of the functions that validates some aspect of a temporal condition.
  They all accept temporal as an argument and return a list of errors."
  [temporal-days-are-valid
   start-day-must-be-with-start-date
   end-day-must-be-with-end-date])

(extend-protocol v/Validator
  cmr.search.models.query.TemporalCondition
  (validate
    [temporal]
    (concat
      (mapcat #(% temporal) temporal-validations)
      (v/validate (:date-range-condition temporal)))))

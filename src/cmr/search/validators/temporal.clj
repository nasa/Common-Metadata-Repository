(ns cmr.search.validators.temporal
  "Contains functions for validating temporal condition"
  (:require [clojure.set]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.search.models.query :as qm]
            [cmr.search.validators.validation :as v]))

(defn- start-day-must-be-with-start-date
  "Validates that start-day must be accompanied by a start-date"
  [temporal]
  (let [{:keys [start-day start-date]} temporal]
    (when (and start-day (nil? start-date))
      ["temporal_start_day must be accompanied by a temporal_start"])))

(defn- end-day-must-be-with-end-date
  "Validates that end-day must be accompanied by a end-date"
  [temporal]
  (let [{:keys [end-day end-date]} temporal]
    (when (and end-day (nil? end-date))
      ["temporal_end_day must be accompanied by a temporal_end"])))

(def temporal-validations
  "A list of the functions that validates some aspect of a temporal condition.
  They all accept temporal as an argument and return a list of errors."
  [start-day-must-be-with-start-date
   end-day-must-be-with-end-date])

(extend-protocol v/Validator
  cmr.search.models.query.TemporalCondition
  (validate
    [temporal]
    (concat
      (mapcat #(% temporal) temporal-validations)
      ;; Reused the date range condition validators
      (v/validate (qm/map->DateRangeCondition {:field :temporal
                                               :start-date (:start-date temporal)
                                               :end-date (:end-date temporal)})))))

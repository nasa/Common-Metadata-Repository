(ns cmr.search.services.parameter-converters.temporal
  "Contains functions for parsing, validating and converting temporal query parameters to query conditions"
  (:require [clojure.string :as s]
            [cmr.common.services.errors :as err]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]))

(defn- value-or-nil
  "Return argument string if it is not blank or nil if it is"
  [value]
  (if (s/blank? value) nil value))

(defn map->temporal-condition
  "Returns temporal condition built with the given map with field, start-date, end-date, start-day and end-day"
  [values]
  (let [{:keys [field start-date end-date start-day end-day]} values
        date-range-condition (qm/map->DateRangeCondition {:field field
                                                          :start-date start-date
                                                          :end-date end-date})]
    (qm/map->TemporalCondition {:field field
                                :date-range-condition date-range-condition
                                :start-day start-day
                                :end-day end-day})))

;; Converts temporal parameter and values into query condition, returns the converted condition
(defmethod p/parameter->condition :temporal
  [concept-type param value options]
  (if (sequential? value)
    (qm/or-conds
      (map #(p/parameter->condition concept-type param % options) value))
    (let [[start-date end-date start-day end-day] (map s/trim (s/split value #","))]
      (map->temporal-condition {:field param
                                :start-date (value-or-nil start-date)
                                :end-date (value-or-nil end-date)
                                :start-day (value-or-nil start-day)
                                :end-day (value-or-nil end-day)}))))

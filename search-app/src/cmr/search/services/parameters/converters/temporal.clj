(ns cmr.search.services.parameters.converters.temporal
  "Contains functions for parsing, validating and converting temporal query parameters to query conditions"
  (:require
   [clojure.string :as string]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.date-time-range-parser :as range-parser]
   [cmr.search.models.query :as qm]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.es-params-converter :as p]))

(defn string->int-value
  "Return int value of the string value if it is not blank or nil if it is"
  [value]
  (when-not (string/blank? value) (Integer/parseInt value)))

;; Converts temporal parameter and values into query condition, returns the converted condition
(defmethod p/parameter->condition :temporal
  [context concept-type param value options]
  (if (sequential? value)
    (if (= "true" (get-in options [:temporal :and]))
      (gc/and-conds
        (map #(p/parameter->condition context concept-type param % options) value))
      (gc/or-conds
        (map #(p/parameter->condition context concept-type param % options) value)))
    (if (re-find #"/" value)
      (let [[iso-range start-day end-day] (map string/trim (string/split value #","))
            date-time-range (when-not (string/blank? iso-range)
                              (range-parser/parse-datetime-range iso-range))]
        (qm/map->TemporalCondition {:start-date (:start-date date-time-range)
                                    :end-date (:end-date date-time-range)
                                    :start-day (string->int-value start-day)
                                    :end-day (string->int-value end-day)
                                    :exclusive? (= "true" (get-in options [:temporal :exclude-boundary]))
                                    :limit-to-granules (= "true" (get-in options [:temporal :limit-to-granules]))}))
      (let [[start-date end-date start-day end-day] (map string/trim (string/split value #","))]
        (qm/map->TemporalCondition {:start-date (when-not (string/blank? start-date) (parser/parse-datetime start-date))
                                    :end-date (when-not (string/blank? end-date) (parser/parse-datetime end-date))
                                    :start-day (string->int-value start-day)
                                    :end-day (string->int-value end-day)
                                    :exclusive? (= "true" (get-in options [:temporal :exclude-boundary]))
                                    :limit-to-granules (= "true" (get-in options [:temporal :limit-to-granules]))})))))

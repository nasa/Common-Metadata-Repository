(ns cmr.search.services.aql.converters.temporal
  "Contains functions for parsing, validating and converting temporal aql element to query conditions"
  (:require [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.services.parameters.converters.temporal :as pt]
            [cmr.search.models.query :as q]))

;; Converts temporal element into query condition, returns the converted condition
(defmethod a/element->condition :temporal
  [concept-type element]
  (let [[start-date stop-date] (a/parse-date-range-element element)
        start-day (:value (cx/attrs-at-path element [:startDay]))
        end-day (:value (cx/attrs-at-path element [:endDay]))]
    (q/map->TemporalCondition {:start-date start-date
                               :end-date stop-date
                               :start-day (pt/string->int-value start-day)
                               :end-day (pt/string->int-value end-day)})))
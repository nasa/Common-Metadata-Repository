(ns cmr.search.services.parameters.converters.equator-crossing-date
  "Contains functions for converting equator-crossing-date search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.params :as p]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.common.date-time-range-parser :as parser])
  (:import clojure.lang.ExceptionInfo))

;; Converts equator-crossing-date parameter into a query condition
(defmethod p/parameter->condition :equator-crossing-date
  [concept-type param values options]
  (try
    (let [ec-map (parser/parse-datetime-range values)]
      (qm/map->EquatorCrossingDateCondition ec-map))
    (catch ExceptionInfo e
      (errors/internal-error! (msg/date-time-range-failed-validation) e))))
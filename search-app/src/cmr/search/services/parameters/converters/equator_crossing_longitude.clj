(ns cmr.search.services.parameters.converters.equator-crossing-longitude
  "Contains functions for converting equator-crossing-longitude search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.params :as p]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as parser])
  (:import [cmr.search.models.query
            EquatorCrossingLongitudeRangeCondition
            EquatorCrossingLongitudeValueCondition]
           clojure.lang.ExceptionInfo))

(defn- equator-crossing-longitude-param-str->condition
  [param-str]
  (try
    (let [{:keys [value] :as on-map} (parser/numeric-range-parameter->map param-str)]
      (if value
        (qm/map->EquatorCrossingLongitudeValueCondition on-map)
        (qm/map->EquatorCrossingLongitudeRangeCondition on-map)))
    (catch ExceptionInfo e
      (errors/internal-error! (msg/non-numeric-value-failed-validation) e))))

(defn- equator-crossing-longitude-param-map->condition
  [eql-map]
  (try
    (let [numeric-map (into {} (for [[k v] eql-map] [k (Double. v)]))
          {:keys [value]} numeric-map]
      (if value
        (qm/map->EquatorCrossingLongitudeValueCondition numeric-map)
        (qm/map->EquatorCrossingLongitudeRangeCondition numeric-map)))
    (catch NumberFormatException e
      (errors/internal-error! (msg/non-numeric-value-failed-validation) e))))


;; Converts orbit-number parameter into a query condition
(defmethod p/parameter->condition :equator-crossing-longitude
  [_context concept-type param values options]
  (if (string? values)
    (equator-crossing-longitude-param-str->condition values)
    (equator-crossing-longitude-param-map->condition values)))

(ns cmr.search.services.parameters.converters.orbit-number
  "Contains functions for converting orbit number search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.params :as p]
            [clojure.string :as str]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as parser])
  (:import [cmr.search.models.query
            OrbitNumberValueCondition
            OrbitNumberRangeCondition]
           clojure.lang.ExceptionInfo))

(defn- orbit-number-param-str->condition
  [param-str]
  (let [{:keys [value] :as on-map} (parser/numeric-range-parameter->map param-str)]
    (if value
      (qm/map->OrbitNumberValueCondition on-map)
      (qm/map->OrbitNumberRangeCondition on-map))))

(defn- orbit-number-param-map->condition
  [on-map]
  (try
    (let [numeric-map (into {} (for [[k v] on-map] [k (Double. v)]))
          {:keys [value]} numeric-map]
      (if value
        (qm/map->OrbitNumberValueCondition numeric-map)
        (qm/map->OrbitNumberRangeCondition numeric-map)))
    (catch NumberFormatException e
      (errors/internal-error! msg/non-numeric-orbit-number-parameter))))


;; Converts orbit-number parameter into a query condition
(defmethod p/parameter->condition :orbit-number
  [_context concept-type param values options]
  (if (string? values)
    (orbit-number-param-str->condition values)
    (orbit-number-param-map->condition values)))

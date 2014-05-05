(ns cmr.search.services.parameter-converters.orbit-number
  "Contains functions for converting orbit number search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]
            [clojure.string :as str]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors])
  (:import [cmr.search.models.query
            OrbitNumberValueCondition
            OrbitNumberRangeCondition]
           clojure.lang.ExceptionInfo))

(defn orbit-number-str->orbit-number-map
  "Convert an orbit-number string to a map with exact or range values."
  [ons]
  (if-let [[_ ^java.lang.String start ^java.lang.String stop] (re-find #"^(.*),(.*)$" ons)]
    {:min-orbit-number (Double. start)
     :max-orbit-number (Double. stop)}
    {:orbit-number (Double. ons)}))

(defn map->orbit-number-range-condition
  "Build an orbit number condition with a numeric range."
  [values]
  (let [{:keys [min-orbit-number max-orbit-number]} values]
    (qm/map->OrbitNumberRangeCondition {:min-value min-orbit-number :max-value max-orbit-number})))


;; Converts orbit-number paramter into a query condition
(defmethod p/parameter->condition :orbit-number
  [concept-type param values options]
  (let [{:keys [orbit-number] :as on-map} (orbit-number-str->orbit-number-map values)]
    (if orbit-number
      (qm/map->OrbitNumberValueCondition on-map)
      (map->orbit-number-range-condition on-map))))

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
  (if-let[[_ start stop] (re-find #"^(.*),(.*)$" ons)]
    {:min-orbit-number (Double. start)
     :max-orbit-number (Double. stop)}
    {:orbit-number (Double. ons)}))

(defn map->orbit-number-range-condition
  "Build an orbit number condition with a numeric range."
  [values]
  (let [{:keys [min-orbit-number max-orbit-number]} values
        start-range-condition (qm/map->NumericRangeCondition {:field :start-orbit-number
                                                              :min-value min-orbit-number
                                                              :max-value max-orbit-number})
        orbit-number-range-condition (qm/map->NumericRangeCondition {:field :orbit-number
                                                                     :min-value min-orbit-number
                                                                     :max-value max-orbit-number})
        stop-range-condition (qm/map->NumericRangeCondition {:field :stop-orbit-number
                                                             :min-value min-orbit-number
                                                             :max-value max-orbit-number})]
    (qm/map->OrbitNumberRangeCondition {:start-orbit-number-range-condition start-range-condition
                                        :orbit-number-range-condition orbit-number-range-condition
                                        :stop-orbit-number-range-condition stop-range-condition})))


;; Converts orbit-number paramter into a query condition
(defmethod p/parameter->condition :orbit-number
  [concept-type param values options]
  (let [{:keys [orbit-number] :as on-map} (orbit-number-str->orbit-number-map values)]
    (if orbit-number
      (qm/map->OrbitNumberValueCondition on-map)
      (map->orbit-number-range-condition on-map))))

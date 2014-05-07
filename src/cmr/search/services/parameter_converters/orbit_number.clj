(ns cmr.search.services.parameter-converters.orbit-number
  "Contains functions for converting orbit number search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.search.services.parameters :as p]
            [clojure.string :as str]
            [cmr.search.services.messages.orbit-number-messages :as msg]
            [cmr.common.services.errors :as errors]
            [cmr.common.parameter-parser :as parser])
  (:import [cmr.search.models.query
            OrbitNumberValueCondition
            OrbitNumberRangeCondition]
           clojure.lang.ExceptionInfo))

;; Converts orbit-number parameter into a query condition
(defmethod p/parameter->condition :orbit-number
  [concept-type param values options]
  (let [{:keys [value] :as on-map} (parser/numeric-range-parameter->map values)]
    (if value
      (qm/map->OrbitNumberValueCondition on-map)
      (qm/map->OrbitNumberRangeCondition on-map))))

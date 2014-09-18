(ns cmr.search.services.aql.converters.two-d-coordinate-system
  "Contains functions for converting TwoDCoordinateSystem aql element to query conditions"
  (:require [cmr.common.xml :as cx]
            [clojure.string :as s]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as qm]))

(defn- element->coordinate-cond
  "Returns the two d coordinate condition for the given element and coordinate index"
  [element idx]
  (let [coord-key (keyword (str "coordinate" idx))]
    (try
      (if-let [value (cx/double-at-path element [coord-key :value])]
        (when value (qm/->CoordinateValueCondition value))
        (let [element (cx/element-at-path element [coord-key])
              {:keys [min-value max-value]} (a/element->num-range :granule element)]
          (when (and min-value max-value (> min-value max-value))
            (errors/throw-service-error
              :bad-request
              (format "TwoDCoordinateSystem %s range lower [%s] should be smaller than upper [%s]"
                      (name coord-key) min-value max-value)))
          (when (or min-value max-value) (qm/->CoordinateRangeCondition min-value max-value))))
      (catch NumberFormatException e
        (errors/throw-service-error
          :bad-request (format "Invalid format for %s, it should be numeric" (name coord-key)))))))

;; Converts TwoDCoordinateSystem element into query condition, returns the converted condition
(defmethod a/element->condition :two-d-coordinate-system
  [concept-type element]
  (let [two-d-name (cx/string-at-path element [:TwoDCoordinateSystemName :value])
        case-sensitive? (a/aql-elem-case-sensitive?
                          (cx/element-at-path element [:TwoDCoordinateSystemName :value]))
        coord-1-cond (element->coordinate-cond element 1)
        coord-2-cond (element->coordinate-cond element 2)
        condition (when (or coord-1-cond coord-2-cond)
                    [(qm/map->TwoDCoordinateCondition {:coordinate-1-cond coord-1-cond
                                                       :coordinate-2-cond coord-2-cond})])]
    (if (s/blank? two-d-name)
      (errors/throw-service-error
        :bad-request
        "TwoDCoordinateSystemName can not be empty")
      (qm/map->TwoDCoordinateSystemCondition
        {:two-d-name two-d-name
         :two-d-conditions condition
         :case-sensitive? case-sensitive?}))))
(ns cmr.search.services.parameters.converters.two-d-coordinate-system
  "Contains functions for converting two d coordinate system search parameters to a query model."
  (:require [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common-app.services.search.params :as p]
            [clojure.string :as s]
            [cmr.common.services.errors :as errors])
  (:import [cmr.search.models.query
            CoordinateValueCondition
            CoordinateRangeCondition
            TwoDCoordinateCondition
            TwoDCoordinateSystemCondition]
           clojure.lang.ExceptionInfo))

(defn- string->double
  "Returns the double value of the given string if it is not empty"
  [str]
  (when-not (empty? str) (Double. str)))

(defn- string->coordinate-values
  "Returns a list of coordinate values for the given search coordinate
  which is in the format of 5 or 5-7"
  [coord-str]
  (let [[x y] (s/split coord-str #"-")]
    (try
      [(string->double x) (string->double y)]
      (catch NumberFormatException e
        (errors/throw-service-error
          :bad-request (format "Grid values [%s] must be numeric value or range" coord-str))))))

(defn string->coordinate-condition
  "Returns a list of coordinate conditions for the given search coordinate string"
  [coord-str]
  (let [coord-str (when coord-str (s/trim coord-str))]
    (when-not (or (empty? coord-str) (= "-" coord-str))
      (let [[x y] (string->coordinate-values coord-str)]
        (if (and (nil? y) (nil? (re-find #"-" coord-str)))
          (qm/->CoordinateValueCondition x)
          (if (and x y (> x y))
            (errors/throw-service-error
              :bad-request
              (format "Invalid grid range for [%s]" coord-str))
            (qm/->CoordinateRangeCondition x y)))))))

(defn string->TwoDCoordinateCondition
  "Returns a map of search coordinates for the given string
  which is in the format of 5,10 or 5-7,1-6, etc."
  [coordinates-str]
  (let [[coord-1 coord-2] (s/split coordinates-str #",")
        coord-1-cond (string->coordinate-condition coord-1)
        coord-2-cond (string->coordinate-condition coord-2)]
    (qm/map->TwoDCoordinateCondition {:coordinate-1-cond coord-1-cond
                                      :coordinate-2-cond coord-2-cond})))

(defn- validate-two-d-param-str
  "Validate the two-d parameter string, throws error if it is invalid."
  [concept-type param-str]
  (let [[two-d-name two-d-coord-str] (s/split param-str #":" 2)
        param-name (if (= :collection concept-type) "two_d_coordinate_system[name]" "Grid name")
        msg (if (s/blank? param-str)
              (format "%s can not be empty" param-name)
              (format "%s can not be empty, but is for [%s]" param-name param-str))]
    (when (s/blank? two-d-name)
      (errors/throw-service-error :bad-request msg))

    (when (and (= :collection concept-type) (not-empty two-d-coord-str))
      (errors/throw-service-error
        :bad-request
        "two_d_coordinate_system[coordinates] is not supported for collection search."))))

(defn two-d-param-str->condition
  [concept-type param-str]
  (validate-two-d-param-str concept-type param-str)
  (let [[two-d-name two-d-coord-str] (s/split param-str #":" 2)
        coordinate-conds (when-not (empty? two-d-coord-str)
                           (->> (s/split two-d-coord-str #":")
                                (map string->TwoDCoordinateCondition)
                                (remove nil?)
                                seq))]
    (qm/map->TwoDCoordinateSystemCondition {:two-d-name two-d-name
                                            :two-d-conditions coordinate-conds})))

;; Converts two-d-coordinate-system parameter into a query condition
(defmethod p/parameter->condition :two-d-coordinate-system
  [concept-type param values options]
  (if (string? values)
    (two-d-param-str->condition concept-type values)
    (gc/or-conds (map (partial two-d-param-str->condition concept-type) values))))

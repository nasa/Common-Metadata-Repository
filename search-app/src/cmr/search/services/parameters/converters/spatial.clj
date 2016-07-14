(ns cmr.search.services.parameters.converters.spatial
  "Contains parameter converters for spatial parameters"
  (:require [cmr.common-app.services.search.params :as p]
            [cmr.search.models.query :as qm]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.spatial.codec :as spatial-codec]
            [cmr.common.services.errors :as errors]
            [clojure.string :as str]))

(defn url-value->spatial-condition
  [type value]
  (let [shape (spatial-codec/url-decode type value)]
    (when-let [errors (:errors shape)]
      (errors/internal-error!
        (format "Shape format was invalid [%s]. Issues should have be handled in validation."
                (str/join ", " errors))))
    (qm/->SpatialCondition shape)))

(defn url-value->spatial-conditions
  [type value]
  ;; Note: value can be a single string or a vector of strings. (flatten [value])
  ;; converts the value to a sequence of strings irrespective of the type
  (gc/and-conds (map (partial url-value->spatial-condition type) (flatten [value]))))

(defmethod p/parameter->condition :polygon
  [_context concept-type param value options]
  (url-value->spatial-conditions :polygon value))

(defmethod p/parameter->condition :bounding-box
  [_context concept-type param value options]
  (url-value->spatial-conditions :bounding-box value))

(defmethod p/parameter->condition :point
  [_context concept-type param value options]
  (url-value->spatial-conditions :point value))

(defmethod p/parameter->condition :line
  [_context concept-type param value options]
  (url-value->spatial-conditions :line value))

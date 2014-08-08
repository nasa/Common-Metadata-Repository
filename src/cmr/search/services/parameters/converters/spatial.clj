(ns cmr.search.services.parameters.converters.spatial
  "Contains parameter converters for spatial parameters"
  (:require [cmr.search.services.parameters.conversion :as p]
            [cmr.search.models.query :as qm]
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

(defmethod p/parameter->condition :polygon
  [concept-type param value options]
  (url-value->spatial-condition :polygon value))

(defmethod p/parameter->condition :bounding-box
  [concept-type param value options]
  (url-value->spatial-condition :bounding-box value))

(defmethod p/parameter->condition :point
  [concept-type param value options]
  (url-value->spatial-condition :point value))

(defmethod p/parameter->condition :line
  [concept-type param value options]
  (url-value->spatial-condition :line value))
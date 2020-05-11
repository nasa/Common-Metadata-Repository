(ns cmr.search.services.parameters.converters.spatial
  "Contains parameter converters for spatial parameters"
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]
   [cmr.spatial.circle :as spatial-circle]
   [cmr.spatial.codec :as spatial-codec]))

(defconfig points-on-circle
  "The number of vertices of the polygon used to approximate a circle"
  {:default 32 :type Long})

(defn- shape->search-shape
  "Returns the search shape of the given shape. For cirle, we convert it into a poygon to search."
  [shape]
  (if (= cmr.spatial.circle.Circle (type shape))
    (spatial-circle/circle->polygon shape (points-on-circle))
    shape))

(defn url-value->spatial-condition
  [type value]
  (let [shape (spatial-codec/url-decode type value)]
    (when-let [errors (:errors shape)]
      (errors/internal-error!
        (format "Shape format was invalid [%s]. Issues should have been handled in validation."
                (string/join ", " errors))))
    (qm/->SpatialCondition (shape->search-shape shape))))

(defn url-value->spatial-conditions
  [type value]
  ;; Note: value can be a single string or a vector of strings. (flatten [value])
  ;; converts the value to a sequence of strings irrespective of the type
  (gc/and-conds
   (map (partial url-value->spatial-condition type) (flatten [value]))))

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

(defmethod p/parameter->condition :circle
  [_context concept-type param value options]
  (url-value->spatial-conditions :circle value))

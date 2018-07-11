(ns cmr.opendap.geom.impl.esri
  "See the following links:
  * http://esri.github.io/geometry-api-java/javadoc/
  * https://github.com/Esri/geometry-api-java/wiki"
  (:require
   [cmr.opendap.geom.util :as util])
  (:import
   (com.esri.core.geometry Polygon)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- -add-points!
  [self points]
  (->> points
       (partition 2)
       (mapv (fn [[lat lon]]
               (let [[x y] (util/latlon->WGS84 lat lon)]
                 (.lineTo self x y))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn area
  "Returns area in m^2 units."
  [this]
  (.calculateArea2D this))

(defn intersection
  [this other]
  )

(defn create
  "Polygon points are provided in counter-clockwise order. The last point
  should match the first point to close the polygon. The values are listed
  comma separated in longitude latitude order, i.e.:

    [lon1 lat1 lon2 lat2 lon3 lat3 ...]

  Returns area in m^2 units."
  [[first-lat first-lon & points]]
  (let [polygon (new Polygon)
        [start-x start-y] (util/latlon->WGS84 first-lat first-lon)]
    (.startPath polygon start-x start-y)
    (-add-points! polygon points)
    polygon))

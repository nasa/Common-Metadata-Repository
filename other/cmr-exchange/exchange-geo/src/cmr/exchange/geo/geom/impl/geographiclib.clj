(ns cmr.exchange.geo.geom.impl.geographiclib
  "See the following:
  * https://geographiclib.sourceforge.io/html/java/
  * https://sourceforge.net/p/geographiclib/code/ci/release/tree/java/planimeter/src/main/java/Planimeter.java#l6"
  (:import
   (net.sf.geographiclib Geodesic PolygonArea))
  (:refer-clojure :exclude [intersection]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-geodesic Geodesic/WGS84)
(def default-polyline? false) ; if a closed polygon, polyline needs to be false

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- -add-points!
  [self points]
  (mapv (fn [[lat lon]] (.AddPoint self lat lon)) (partition 2 points)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord GeographiclibPolygon [native])

(defn area
  "Returns area in m^2 units."
  [this]
  (-> this
      :native
      (.Compute)
      (.area)))

(defn intersection
  [this other]
  )

(def behaviour {:area area
                :intersection intersection})

(defn create
  "Polygon points are provided in counter-clockwise order. The last point
  should match the first point to close the polygon. The values are listed
  comma separated in longitude latitude order, i.e.:

    [lon1 lat1 lon2 lat2 lon3 lat3 ...]

  Returns area in m^2 units."
  ([points]
   (create points default-geodesic))
  ([points geodesic]
   (create points geodesic default-polyline?))
  ([points geodesic polyline?]
   (let [polygon (new PolygonArea geodesic polyline?)]
     (-add-points! polygon points)
     (map->GeographiclibPolygon {:native polygon}))))

(ns cmr.opendap.geom.impl.geographiclib
  "See the following:
  * https://geographiclib.sourceforge.io/html/java/
  * https://sourceforge.net/p/geographiclib/code/ci/release/tree/java/planimeter/src/main/java/Planimeter.java#l6"
  (:import
   (net.sf.geographiclib Geodesic PolygonArea)))

(def default-geodesic Geodesic/WGS84)
(def default-polyline? false) ; if a closed polygon, polyline needs to be false

(defn add-points!
  [self points]
  (mapv (fn [[lat lon]] (.AddPoint self lat lon)) (partition 2 points)))

(defn polygon-area
  "Polygon points are provided in counter-clockwise order. The last point
  should match the first point to close the polygon. The values are listed
  comma separated in longitude latitude order, i.e.:

    [lon1 lat1 lon2 lat2 lon3 lat3 ...]

  Returns area in m^2 units."
  ([points]
   (polygon-area points default-geodesic))
  ([points geodesic]
   (polygon-area points geodesic default-polyline?))
  ([points geodesic polyline?]
   (let [polygon (new PolygonArea geodesic polyline?)]
     (add-points! polygon points)
     (-> polygon
         (.Compute)
         (.area)))))

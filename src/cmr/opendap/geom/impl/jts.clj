(ns cmr.opendap.geom.impl.jts
  (:require
   [cmr.opendap.geom.util :as util])
  (:import
   (com.vividsolutions.jts.geom Coordinate GeometryFactory)))

(defn create-coords
  [points]
  (->> points
       (partition 2)
       (mapv (fn [[lat lon]]
        (let [[x y] (util/latlon->WGS84 lat lon)]
          (new Coordinate x y))))
       (into-array)))

(defn polygon-area
  "Polygon points are provided in counter-clockwise order. The last point
  should match the first point to close the polygon. The values are listed
  comma separated in longitude latitude order, i.e.:

    [lon1 lat1 lon2 lat2 lon3 lat3 ...]

  Returns area in m^2 units."
  [points]
  (let [factory (new GeometryFactory)
        ring (.createLinearRing factory (create-coords points))
        polygon (.createPolygon factory ring)]
    (.getArea polygon)))

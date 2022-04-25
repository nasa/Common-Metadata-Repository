(ns cmr.exchange.geo.geom.impl.jts
  "See the following:
  * http://www.tsusiatsoftware.net/jts/javadoc/com/vividsolutions/jts/geom/Geometry.html"
  (:require
   [cmr.exchange.geo.geom.util :as util])
  (:import
   (org.geotools.geometry.jts JTS)
   (org.geotools.referencing CRS)
   (org.geotools.referencing.crs DefaultGeocentricCRS DefaultGeographicCRS)
   (org.locationtech.jts.geom Coordinate GeometryFactory PrecisionModel))
  (:refer-clojure :exclude [empty? intersection reverse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def cartesian-srid 0)
(def wgs84-srid 1)
(def srids {cartesian-srid :cartesian
            wgs84-srid :wgs84})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- -create-coords
  [points]
  (->> points
       (partition 2)
       (mapv (fn [[lat lon]]
         (let [[x y] (util/ll->cartesian lat lon)]
          (new Coordinate x y 0))))
       (into-array)))

(def wgs84->cartesian-xform (CRS/findMathTransform
                             DefaultGeographicCRS/WGS84_3D
                             DefaultGeocentricCRS/CARTESIAN
                             true))

(def cartesian->wgs84-xform (CRS/findMathTransform
                             DefaultGeocentricCRS/CARTESIAN
                             DefaultGeographicCRS/WGS84_3D
                             true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn area
  "Returns area in m^2 units."
  [this]
  (.getArea this))

(defn bounding-box
  [this]
  (.getEnvelope this))

(defn empty?
  [this]
  (.isEmpty this))

(defn intersection
  [this other]
  (.intersection this other))

(defn intersects?
  [this other]
  (.intersects this other))

(defn points
  [this]
  (mapv #(vector (.-x %) (.-y %) (.-z %)) (.getCoordinates this)))

(defn point-count
  [this]
  (.getNumPoints this))

(defn reverse
  [this]
  (.reverse this))

(defn valid?
  [this]
  (.isValid this))

(defn cartesian->wgs84
  [this]
  (JTS/transform this cartesian->wgs84-xform))

(defn wgs84->cartesian
  [this]
  (JTS/transform this wgs84->cartesian-xform))

(def behaviour {:area area
                :bounding-box bounding-box
                :empty? empty?
                :intersection intersection
                :intersects? intersects?
                :points points
                :point-count point-count
                :reverse reverse
                :valid? valid?
                ;; Experimental
                :cartesian->wgs84 cartesian->wgs84
                :wgs84->cartesian wgs84->cartesian})

(defn create
  "Polygon points are provided in counter-clockwise order. The last point
  should match the first point to close the polygon. The values are listed
  comma separated in longitude latitude order, i.e.:

    [lon1 lat1 lon2 lat2 lon3 lat3 ...]"
  [points]
  (let [factory (new GeometryFactory (new PrecisionModel PrecisionModel/FLOATING)
                                     wgs84-srid)]
    (.createPolygon factory (-create-coords points))))

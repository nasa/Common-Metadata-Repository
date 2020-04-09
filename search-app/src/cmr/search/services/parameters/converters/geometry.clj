(ns cmr.search.services.parameters.converters.geometry
  "Contains parameter converters for shapefile parameter"
  (:require
   [cmr.common.log :refer [debug]]
   [cmr.search.models.query :as qm]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.point :as point]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.ring-relations :as rr]
   [clojure.math.numeric-tower :as math])
  (:import
   (java.io BufferedReader File FileReader FileOutputStream FileInputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.net URL)
   (java.util HashMap)
   (java.util.zip ZipInputStream)
   (org.apache.commons.io FilenameUtils)
   (org.geotools.data DataStoreFinder FileDataStoreFinder Query)
   (org.geotools.data.simple SimpleFeatureSource)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.util URLs)
   (org.locationtech.jts.algorithm Orientation)
   (org.locationtech.jts.geom Coordinate Geometry GeometryFactory LineString Point Polygon PrecisionModel)))

(defn geometry?
  "Returns true if the object is of type Geometry"
  [object]
  (instance? org.locationtech.jts.geom.Geometry object))

(defn coords->ords
  "Convert an array of JTS Coordinate to an array of Doubles"
  [coords]
  (mapcat (fn [^Coordinate coord] [(.getX coord) (.getY coord)]) coords))

(defn line-string-ring->ring
  "Convert a JTS LineString to a spatial lib ring"
  [^LineString line-string]
  (rr/ords->ring :geodetic (coords->ords (.getCoordinates line-string))))

(defn force-ccw-orientation
  "Forces a LineString to be in counter-clockwise orientation"
  [^LineString line-string winding]
  (let [coords (.getCoordinates line-string)]
    (if (and 
          (> (count coords) 3)
          (= winding :cw))
      (.reverse line-string)
      line-string)))

(defn polygon->shape
  "Convert a JTS Polygon to a spatial lib shape that can be used in a Spatial query.
  The `options` map can be used to provide information about winding. Accepted keys
  are `:boundary-winding` and `:hole-winding`. Accepted values are `:cw` and `:ccw`."
  [^Polygon polygon options]
  (let [boundary-ring (.getExteriorRing polygon)
        _ (debug (format "BOUNDARY RING BEFORE FORCE-CCW: %s" boundary-ring))
        boundary-ring (force-ccw-orientation boundary-ring (:boundary-winding options))
        _ (debug (format "BOUNDARY RING AFTER FORCE-CCW: %s" boundary-ring))
        num-interior-rings (.getNumInteriorRing polygon)
        interior-rings (if (> num-interior-rings 0)
                         (for [i (range num-interior-rings)]
                           (force-ccw-orientation (.getInteriorRingN polygon i) (:hole-winding options)))
                         [])
        all-rings (concat [boundary-ring] interior-rings)]
    (debug (format "NUM INTERIOR RINGS: [%d]" num-interior-rings))
    (debug (format "RINGS: [%s]" (vec all-rings)))
    (poly/polygon :geodetic (map line-string-ring->ring all-rings))))

(defn point->shape
  "Convert a JTS Point to a spatial lib shape that can be used in a Spatial query"
  [^Point point]
  (point/point (.getX point) (.getY point)))

(defn line->shape
  "Convert a LineString or LinearRing to a spatial lib shape that can be used in a Spatial query"
  [^LineString line]
  (let [ordinates (coords->ords (.getCoordinates line))]
    (l/ords->line-string :geodetic ordinates)))

(defmulti geometry->condition
  "Convert a Geometry object to a query condition.
  The `options` map can be used to provided additional information."
  (fn [^Geometry geometry options] 
    (.getGeometryType geometry)))
    
(defmethod geometry->condition "MultiPolygon"
  [geometry options]
  (let [shape (polygon->shape geometry options)]
    (qm/->SpatialCondition shape)))

(defmethod geometry->condition "Polygon"
  [geometry options]
  (let [shape (polygon->shape geometry options)]
    (qm/->SpatialCondition shape)))

(defmethod geometry->condition "Point"
  [geometry options]
  (let [shape (point->shape geometry)]
    (qm/->SpatialCondition shape)))

(defmethod geometry->condition "LineString"
  [geometry options]
  (let [shape (line->shape geometry)]
    (qm/->SpatialCondition shape)))

(defmethod geometry->condition "LinearRing"
  [geometry options]
  (let [shape (line->shape geometry)]
    (qm/->SpatialCondition shape)))

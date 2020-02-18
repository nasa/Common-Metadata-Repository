(ns cmr.search.services.parameters.converters.geometry
  "Contains parameter converters for shapefile parameter"
  (:require
   [cmr.common-app.services.search.params :as p]
   [cmr.common.mime-types :as mt]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common.util :as util]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.point :as point]
   [cmr.spatial.geodetic-ring :as gr]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.ring-relations :as rr]
   [cmr.spatial.mbr :as mbr]
   [cmr.common.regex-builder :as rb]
   [clojure.string :as str])
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
   (org.geotools.util URLs)))

(defn geometry?
  "Returns true if the object is of type Geometry"
  [object]
  (instance? org.locationtech.jts.geom.Geometry object))

(defn coords->ords
  "Convert an array of JTS Coordinate to an array of Doubles"
  [coords]
  (flatten (map (fn [coord] [(.getX coord) (.getY coord)]) coords)))

(defn line-string-ring->ring
  "Convert a JTS LineString to a spatial lib ring"
  [line-string]
  (rr/ords->ring :geodetic (coords->ords (.getCoordinates line-string))))

(defn polygon->shape
  "Convert a JTS Polygon to a spatial lib shape that can be used in a Spatial query"
  [polygon]
  (let [boundary-ring (.reverse (.getExteriorRing polygon))
        num-interior-rings (.getNumInteriorRing polygon)
        interior-rings (if (> num-interior-rings 0)
                         (for [i (range num-interior-rings)]
                           (.getInteriorRingN polygon i))
                         [])
        all-rings (concat [boundary-ring] interior-rings)]
    (poly/polygon :geodetic (map line-string-ring->ring all-rings))))

(defn point->shape
  "Convert a JTS Point to a spatial lib shape that can be used in a Spatial query"
  [point]
  (point/point (.getX point) (.getY point)))

(defn line->shape
  "Convert a LineString or LinearRing to a spatial lib shape that can be used in a Spatial query"
  [line]
  (let [ordinates (coords->ords (.getCoordinates line))]
    (l/ords->line-string :geodetic ordinates)))

; (defmethod url-decode :point
;   [type s]
;   (if-let [match (re-matches point-regex s)]
;     (let [[_ ^String lon-s ^String lat-s] match]
;       (point/point (Double. lon-s) (Double. lat-s)))
;     {:errors [(smsg/shape-decode-msg :point s)]}))

; (defmethod url-decode :bounding-box
;   [type s]
;   (if-let [match (re-matches mbr-regex s)]
;     (let [[_
;            ^String w
;            ^String s
;            ^String e
;            ^String n] match]
;       (mbr/mbr (Double. w) (Double. n) (Double. e) (Double. s)))
;     {:errors [(smsg/shape-decode-msg :bounding-box s)]}))

; (defmethod url-decode :polygon
;   [type s]
;   (if-let [match (re-matches polygon-regex s)]
;     (let [ordinates (map #(Double. ^String %) (str/split s #","))]
;       (poly/polygon :geodetic [(rr/ords->ring :geodetic ordinates)]))
;     {:errors [(smsg/shape-decode-msg :polygon s)]}))

; (defmethod url-decode :line
;   [type s]
;   (if-let [match (re-matches line-regex s)]
;     (let [ordinates (map #(Double. ^String %) (str/split s #","))]
;       (l/ords->line-string :geodetic ordinates))
;     {:errors [(smsg/shape-decode-msg :line s)]}))

(defmulti geometry->condition
  "Convert a Geometry object to a query condition"
  (fn [geometry] (.getGeometryType geometry)))

(defmethod geometry->condition "Polygon"
  [geometry]
  (let [shape (polygon->shape geometry)
        condition (qm/->SpatialCondition shape)
        _ (println condition)]
    condition))

(defmethod geometry->condition "Point"
  [geometry]
  (let [shape (point->shape geometry)
        condition (qm/->SpatialCondition shape)
        _ (println condition)]
    condition))

(defmethod geometry->condition "LineString"
  [geometry]
  (let [shape (line->shape geometry)
        condition (qm/->SpatialCondition shape)
        _ (println condition)]
    condition))

(defmethod geometry->condition "LinearRing"
  [geometry]
  (let [shape (line->shape geometry)
        condition (qm/->SpatialCondition shape)
        _ (println condition)]
    condition))

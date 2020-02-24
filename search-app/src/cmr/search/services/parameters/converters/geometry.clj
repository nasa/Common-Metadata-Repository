(ns cmr.search.services.parameters.converters.geometry
  "Contains parameter converters for shapefile parameter"
  (:require
   [cmr.search.models.query :as qm]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.point :as point]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.ring-relations :as rr])
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
   (org.locationtech.jts.geom Coordinate Geometry LineString Point Polygon)))

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

(defn polygon->shape
  "Convert a JTS Polygon to a spatial lib shape that can be used in a Spatial query"
  [^Polygon polygon]
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
  [^Point point]
  (point/point (.getX point) (.getY point)))

(defn line->shape
  "Convert a LineString or LinearRing to a spatial lib shape that can be used in a Spatial query"
  [^LineString line]
  (let [ordinates (coords->ords (.getCoordinates line))]
    (l/ords->line-string :geodetic ordinates)))

(defmulti geometry->condition
  "Convert a Geometry object to a query condition"
  (fn [^Geometry geometry] (.getGeometryType geometry)))

(defmethod geometry->condition "Polygon"
  [geometry]
  (let [shape (polygon->shape geometry)
        condition (qm/->SpatialCondition shape)]
    condition))

(defmethod geometry->condition "Point"
  [geometry]
  (let [shape (point->shape geometry)
        condition (qm/->SpatialCondition shape)]
    condition))

(defmethod geometry->condition "LineString"
  [geometry]
  (let [shape (line->shape geometry)
        condition (qm/->SpatialCondition shape)]
    condition))

(defmethod geometry->condition "LinearRing"
  [geometry]
  (let [shape (line->shape geometry)
        condition (qm/->SpatialCondition shape)]
    condition))

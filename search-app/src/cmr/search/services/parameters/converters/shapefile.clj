(ns cmr.search.services.parameters.converters.shapefile
  "Contains parameter converters for shapefile parameter"
  (:require [cmr.common-app.services.search.params :as p]
            [cmr.search.models.query :as qm]
            [cmr.common.mime-types :as mt]
            [cmr.common-app.services.search.group-query-conditions :as gc]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.parameters.converters.geometry :as geo]
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
   (org.geotools.geometry.jts JTS)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)))

(def EPSG-4326-CRS (CRS/decode "EPSG:4326" true))

(defn- unzip-file
  "Unzip a file (of type File) into a temporary directory and return the directory path as a File"
  [file]
  (let [temp-dir (Files/createTempDirectory "Shapes" (into-array FileAttribute []))
        input-stream (FileInputStream. file)
        zip-input-stream (ZipInputStream. input-stream)
        buffer (byte-array 1024)]
    (util/while-let [zip-entry (.getNextEntry zip-input-stream)]
                    (let [file-name (.getName zip-entry)
                          new-file (File. (.toString temp-dir) file-name)
                          _ (println (str "Unzipping to " (.getAbsolutePath new-file)))
                          file-output-stream (FileOutputStream. new-file)]
                      (loop [length (.read zip-input-stream buffer 0 1024)]
                        (do
                          (println length))
                        (if (> length 0)
                          (do
                            (.write file-output-stream buffer 0 length)
                            (recur (.read zip-input-stream buffer 0 1024)))
                          (do
                            (.close file-output-stream)
                            (.closeEntry zip-input-stream))))))
    (.closeEntry zip-input-stream)
    (.close zip-input-stream)
    (.close input-stream)
    (.toFile temp-dir)))

(defn find-shp-file
  "Find the .shp file in the given directory (File) and return it as a File"
  [dir]
  (let [files (file-seq dir)]
    (first (filter #(= "shp" (FilenameUtils/getExtension (.getAbsolutePath %))) files))))

; (defn print-feature
;   "Print the contents of a Feature"
;   [feature]
;   (let [source-geometry (-> (.getDefaultGeometryProperty feature) .getValue)
;         num-geometries (.getNumGeometries source-geometry)]
;     (when  (> num-geometries 1)
;       (println (.getGeometryType source-geometry)))
;     (doseq [index (range 0 num-geometries)
;             :let [geometry (.getGeometryN source-geometry index)
;                   coords (.getCoordinates geometry)]]
;       (println (format "%s %d" (.getGeometryType geometry) index))
;       (doseq [coord coords]
;         (println (format "(%f, %f)" (.-x coord) (.-y coord)))))))

(defn geometry->conditions
  "Get one or more conditions for the given Geometry. This will only
  return more than one condition if the Geometry is a GeometryCollection."
  [geometry]
  (let [num-geometries (.getNumGeometries geometry)]
    (for [index (range 0 num-geometries)
          :let [sub-geometry (.getGeometryN geometry index)]]
      (geo/geometry->condition sub-geometry))))

(defn transform-to-epsg-4326
  "Transform the geometry to WGS84 (EPSG-4326) CRS if is not already"
  [geometry src-crs]
  ;; if the source CRS is defined and not already EPSG-4326 then transform the geometry to WGS84
  (if (and src-crs
           (not (= (.getName src-crs) (.getName EPSG-4326-CRS))))
    (let [_ (println (.getName src-crs))
          _ (println (CRS/getAxisOrder src-crs))
          _ (println (.getName EPSG-4326-CRS))
          _ (println (CRS/getAxisOrder EPSG-4326-CRS))
          transform (CRS/findMathTransform src-crs EPSG-4326-CRS false)
          new-geometry (JTS/transform geometry transform)
          _ (println new-geometry)]
      new-geometry)
    geometry))

(defn feature->conditions
  "Process the contents of a Feature to return query conditions"
  [feature]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        properties (.getProperties feature)
        _ (println feature)
        _ (println (format "Found [%d] properties" (count properties)))
        _ (doseq [p properties] (println (.getName p)))
        _ (doseq [p properties] (println (.getValue p)))
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties) ;; TODO need to handle Associations here
        _ (println (format "Found [%d] geometries" (count geometry-props)))
        geometries (map #(-> % .getValue (transform-to-epsg-4326 crs)) geometry-props)]
    (flatten (map (fn [g] (geometry->conditions g)) geometries))))

; (defn feature->conditions-bak
;   "Process the contents of a Feature to return query conditions"
;   [feature]
;   (let [source-geometry (-> (.getDefaultGeometryProperty feature) .getValue)
;         num-geometries (.getNumGeometries source-geometry)]
;     (when  (> num-geometries 1)
;       (println (.getGeometryType source-geometry)))
;     (for [index (range 0 num-geometries)
;           :let [geometry (.getGeometryN source-geometry index)]]
;       (geometry->condition geometry))))

; (defn url-value->spatial-conditions
  ; [type value]
  ; ;; Note: value can be a single string or a vector of strings. (flatten [value])
  ; ;; converts the value to a sequence of strings irrespective of the type
  ; (gc/or-conds (map (partial url-value->spatial-condition type) (flatten [value]))))


(defn esri-shapefile->condition-vec
  "Converts a shapefile to a vector of SpatialConditions"
  [shapefile-info]
  (let [file (:tempfile shapefile-info)
        temp-dir (unzip-file file)
        shp-file (find-shp-file temp-dir)
        _ (println (format "SHP FILE: %s" shp-file))
        data-store (FileDataStoreFinder/getDataStore shp-file)
        feature-source (.getFeatureSource data-store)
        collection (.getFeatures feature-source)
        _ (println (format "NUMBER OF FEATURES: %d" (.size collection)))
        iterator (.features collection)]
    (try
      (loop [conditions []]
        (if (.hasNext iterator)
          (let [feature (.next iterator)
                feature-conditions (feature->conditions feature)]
            (if (> (count feature-conditions) 0)
              (recur (conj conditions (gc/or-conds feature-conditions)))
              (recur conditions)))
          conditions))
      (finally (do
                 (.close iterator)
                 (-> data-store .getFeatureReader .close))))))

(defmulti shapefile->conditions
  "Convers a shapefile to query conditions based on shapefile format"
  (fn [shapefile-info]
    (:content-type shapefile-info)))

;; ESRI shapefiles
(defmethod shapefile->conditions mt/shapefile
  [shapefile-info]
  (let [conditions-vec (esri-shapefile->condition-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

(defmethod p/parameter->condition :shapefile
  [_context concept-type param value options]
  (shapefile->conditions value))

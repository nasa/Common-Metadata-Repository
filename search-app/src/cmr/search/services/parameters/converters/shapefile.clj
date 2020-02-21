(ns cmr.search.services.parameters.converters.shapefile
  "Contains parameter converters for shapefile parameter"
  (:require 
    [clojure.java.io :as io]
    [clojure.string :as str]
    [cmr.common.config :as cfg :refer [defconfig]]
    [cmr.common.log :refer (debug)]
    [cmr.common.mime-types :as mt]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.params :as p]
    [cmr.common.services.errors :as errors]
    [cmr.search.models.query :as qm]
    [cmr.search.services.parameters.converters.geometry :as geo]
    [cmr.common.util :as util])
  (:import
   (java.io BufferedInputStream File FileReader FileOutputStream FileInputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.net URL)
   (java.util HashMap)
   (java.util.zip ZipFile ZipInputStream)
   (org.apache.commons.io FilenameUtils)
   (org.geotools.data DataStoreFinder FileDataStoreFinder Query)
   (org.geotools.data.simple SimpleFeatureSource)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.geometry.jts JTS)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)))

(def EPSG-4326-CRS 
  "The CRS object for WGS 84"
  (CRS/decode "EPSG:4326" true))

(defconfig enable-shapefile-parameter-flag
  "Flag that indicates if we allow spatial searching by shapefile."
  {:default false :type Boolean})

(defn- unzip-file
  "Unzip a file (of type File) into a temporary directory and return the directory path as a File"
  [source]
  (let [target-dir (Files/createTempDirectory "Shapes" (into-array FileAttribute []))]
    (try
      (with-open [zip (ZipFile. source)]
        (let [entries (enumeration-seq (.entries zip))
              target-file #(File. (.toString target-dir) (str %))]
          (doseq [entry entries :when (not (.isDirectory ^java.util.zip.ZipEntry entry))
                  :let [f (target-file entry)]]
            (debug (format "Zip file entry: [%s]" (.getName entry)))
            (io/copy (.getInputStream zip entry) f))))
      (.toFile target-dir)
      (catch Exception e
        (.delete (.toFile target-dir))
        (errors/throw-service-error :bad-request (str "Error while uncompressing zip file: " (.getMessage e)))))))

(defn find-shp-file
  "Find the .shp file in the given directory (File) and return it as a File"
  [dir]
  (let [files (file-seq dir)]
    (first (filter #(= "shp" (FilenameUtils/getExtension (.getAbsolutePath %))) files))))

(defn geometry->conditions
  "Get one or more conditions for the given Geometry. This will only
  return more than one condition if the Geometry is a GeometryCollection."
  [geometry]
  (let [num-geometries (.getNumGeometries geometry)]
    (for [index (range 0 num-geometries)
          :let [sub-geometry (.getGeometryN geometry index)]]
      (geo/geometry->condition sub-geometry))))

(defn transform-to-epsg-4326
  "Transform the geometry to WGS84 CRS if is not already"
  [geometry src-crs]
  ;; if the source CRS is defined and not already WGS84 then transform the geometry to WGS84
  (if (and src-crs
           (not (= (.getName src-crs) (.getName EPSG-4326-CRS))))
    (let [src-crs-name (.getName src-crs)]
      (debug (format "Source CRS: [%s]" src-crs-name))
      (debug (format "Source axis order: [%s]" (CRS/getAxisOrder src-crs)))
      (debug (format "Destination CRS: [%s]" (.getName EPSG-4326-CRS)))
      (debug (format "Destination axis order: [%s]" (CRS/getAxisOrder EPSG-4326-CRS)))
      ; If we find a tranform use it to tranform the geometry, 
      ; otherwise send an error message
      (if-let [transform (try 
                          (CRS/findMathTransform src-crs EPSG-4326-CRS false)
                          (catch Exception e))]
        (let [new-geometry (JTS/transform geometry transform)]
          (debug (format "New geometry: [%s" new-geometry))
          new-geometry)
        (errors/throw-service-error :bad-request (format "Cannot transform source CRS [%s] to WGS 84" src-crs-name))))
    geometry))

(defn feature->conditions
  "Process the contents of a Feature to return query conditions"
  [feature]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        properties (.getProperties feature)
        _ (doseq [p properties] (debug (.getName p)))
        _ (doseq [p properties] (debug (.getValue p)))
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties)
        _ (debug (format "Found [%d] geometries" (count geometry-props)))
        geometries (map #(-> % .getValue (transform-to-epsg-4326 crs)) geometry-props)
        _ (debug (format "Transformed [%d] geometries" (count geometries)))]
    (mapcat (fn [g] (geometry->conditions g)) geometries)))

(defn- error-if
  "Throw a service error with the given message if `f` applied to `item` is true. 
  Otherwise just return `item`. Removes the temporary directory `temp-dir` first."
  [item f message ^File temp-dir]
  (if (f item)
    (do
      (.delete temp-dir)
      (errors/throw-service-error :bad-request message))
    item))

(defn esri-shapefile->condition-vec
  "Converts a shapefile to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          ^File temp-dir (unzip-file file)
          shp-file (error-if
                    (find-shp-file temp-dir) 
                    nil?
                    "Incomplete shapefile: missing .shp file"
                    temp-dir)
          data-store (FileDataStoreFinder/getDataStore shp-file)
          feature-source (.getFeatureSource data-store)
          collection (error-if (.getFeatures feature-source)
                              #(< (.size %) 1)
                              "Shapefile has no features"
                              temp-dir)
          _ (debug (format "Found [%d] features" (.size collection)))
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
                  (-> data-store .getFeatureReader .close)
                  (.delete temp-dir)))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defmulti shapefile->conditions
  "Converts a shapefile to query conditions based on shapefile format"
  (fn [shapefile-info]
    (:content-type shapefile-info)))

;; ESRI shapefiles
(defmethod shapefile->conditions mt/shapefile
  [shapefile-info]
  (let [conditions-vec (esri-shapefile->condition-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

(defmethod p/parameter->condition :shapefile
  [_context concept-type param value options]
  (if (enable-shapefile-parameter-flag)
    (shapefile->conditions value)
    (errors/throw-service-error :bad-request "Searching by shapefile is not enabled")))

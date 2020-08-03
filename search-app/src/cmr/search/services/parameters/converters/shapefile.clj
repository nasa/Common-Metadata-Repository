(ns cmr.search.services.parameters.converters.shapefile
  "Contains parameter converters for shapefile parameter"
  (:require
   [clojure.java.io :as io]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.log :refer [debug info]]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common.services.errors :as errors]
   [cmr.search.services.parameters.converters.geojson :as geojson]
   [cmr.search.services.parameters.converters.geometry :as geo])
  (:import
   (java.io File FileInputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.util ArrayList)
   (java.util.zip ZipFile)
   (org.apache.commons.io FilenameUtils)
   (org.geotools.data FileDataStoreFinder)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.geometry.jts JTS)
   (org.geotools.kml.v22 KMLConfiguration)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)
   (org.geotools.xsd PullParser)
   (org.locationtech.jts.geom Geometry)
   (org.opengis.feature.simple SimpleFeature)))

(def EPSG-4326-CRS
  "The CRS object for WGS 84"
  (CRS/decode "EPSG:4326" true))

(defconfig enable-shapefile-parameter-flag
  "Flag that indicates if we allow spatial searching by shapefile."
  {:default false :type Boolean})

(defconfig max-shapefile-features
  "The maximum number of feature a shapefile can have"
  {:default 500 :type Long})

(defconfig max-shapefile-points
  "The maximum number of points a shapefile can have"
  {:default 5000 :type Long})

(defn winding-opts
  "Get the opts for a call to `normalize-polygon-winding` based on file type"
  [mime-type]
  (case mime-type
    "application/shapefile+zip" {:boundary-winding :cw}
    "application/vnd.google-earth.kml+xml" {}
    "application/geo+json" {:hole-winding :cw}))

(defn unzip-file
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
  return more than one condition if the Geometry is a GeometryCollection.
  The `options` map can be used to provided additional information."
  [^Geometry geometry options]
  (let [num-geometries (.getNumGeometries geometry)]
    (debug (format "NUM SUB GEOMETRIES: [%d]" num-geometries))
    (for [index (range 0 num-geometries)
          :let [sub-geometry (.getGeometryN geometry index)]]
      (geo/geometry->condition sub-geometry options))))

(defn geometry-point-count
  "Get the number of points in the given Geometry"
  [^Geometry geometry]
  (let [num-geometries (.getNumGeometries geometry)
        all-geometries  (for [index (range 0 num-geometries)
                              :let [sub-geometry (.getGeometryN geometry index)]]
                          sub-geometry)]
    (reduce (fn [count geometry] (+ count (.getNumPoints geometry))) 0 all-geometries)))

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
      ; If we find a transform use it to transform the geometry, 
      ; otherwise send an error message
      (if-let [transform (try
                           (CRS/findMathTransform src-crs EPSG-4326-CRS false)
                           (catch Exception e))]
        (let [new-geometry (JTS/transform geometry transform)]
          (debug (format "New geometry: [%s" new-geometry))
          new-geometry)
        (errors/throw-service-error :bad-request (format "Cannot transform source CRS [%s] to WGS 84" src-crs-name))))
    geometry))

(defn feature-point-count
  "Get the number of points in the Feature"
  [feature]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        properties (.getProperties feature)
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties)
        geometries (map #(-> % .getValue (transform-to-epsg-4326 crs)) geometry-props)]
    (apply + (map geometry-point-count geometries))))

(defn features-point-count
  "Compute the number of points in a list of Features"
  [features]
  (reduce (fn [total feature] (+ total (feature-point-count feature)))
          0
          features))

(defn validate-point-count
  "Validate that the number of points in the features is greater than zero and less than the limit"
  [features]
  (let [point-count (features-point-count features)]
    (cond
      (= point-count 0) "Shapefile has no points"
      (> point-count (max-shapefile-points)) 
          (format "Number of points in shapefile exceeds the limit of %d"
            (max-shapefile-points))
      :else nil)))

(defn- validate-feature-count
  "Validates that the number of features is greater than zero and less than the limit"
  [features]
  (let [feature-count (count features)]
    (cond
      (= feature-count 0) "Shapefile has no features"
      (> feature-count (max-shapefile-features))
        (format "Shapefile feature count [%d] exceeds the %d feature limit"
          feature-count
          (max-shapefile-features))
      :else nil)))

(defn validate-features
  "Validate this list of features in terms of shapefile limits"
  [features]
  (when-let [message (or (validate-feature-count features) (validate-point-count features))]
    (errors/throw-service-error :bad-request message)))

(defn feature->conditions
  "Process the contents of a Feature to return query conditions along with number of points in
  the processed Feature. The `context` map can be used to pass along additional info."
  [feature context]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        properties (.getProperties feature)
        _ (doseq [p properties] (debug (.getName p)) (debug (.getValue p)))
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties)
        _ (debug (format "Found [%d] geometries" (count geometry-props)))
        geometries (map #(-> % .getValue (transform-to-epsg-4326 crs)) geometry-props)
        _ (debug (format "Transformed [%d] geometries" (count geometries)))
        point-count (apply + (map geometry-point-count geometries))
        conditions (mapcat (fn [g] (geometry->conditions g context)) geometries)]
    (debug (format "CONDITIONS: %s" conditions))
    [conditions point-count]))

(defn features->conditions
  "Converts a list of features into a vector of SpatialConditions"
  [features mime-type]
  (validate-features features)
  (let [iterator (.iterator features)]
  ;; Loop overall all the Features in the list, building up a vector of conditions
    (loop [conditions []]
      (if (.hasNext iterator)
        (let [feature (.next iterator)
              [feature-conditions _] (feature->conditions feature (winding-opts mime-type))]
          (if (> (count feature-conditions) 0)
            ;; if any conditions were created for the feature add them to the current conditions
            (recur (conj conditions (gc/or-conds feature-conditions)))
            (recur conditions)))
        ;; no more Features in list - return conditions created
        conditions))))

(defn error-if
  "Throw a service error with the given message if `f` applied to `item` is true. 
  Otherwise just return `item`. Removes the temporary file/directory `temp-file` first."
  [item f message ^File temp-file]
  (if (f item)
    (do
      (when temp-file (.delete temp-file))
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
          features (.getFeatures feature-source)
          iterator (.features features)
          feature-list (ArrayList.)]
      (try
        (while (.hasNext iterator)
          (let [feature (.next iterator)]
            (.add feature-list feature)))
        (features->conditions feature-list mt/shapefile)
        (finally
          (.close iterator)
          (-> data-store .getFeatureReader .close)
          (.delete temp-dir))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defn geojson->conditions-vec
  "Converts a geojson file to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          _ (geojson/sanitize-geojson file)
          url (URLs/fileToUrl file)
          data-store (GeoJSONDataStore. url)
          feature-source (.getFeatureSource data-store)
          features (.getFeatures feature-source)
          ;; Fail fast
          _ (when (or (nil? features) 
                      (nil? (.getSchema features))
                      (.isEmpty features))
              (errors/throw-service-error :bad-request "Shapefile has no features"))
          iterator (.features features)
          feature-list (ArrayList.)]
      (try
        (while (.hasNext iterator)
          (let [feature (.next iterator)]
            (.add feature-list feature)))
        (features->conditions feature-list mt/geojson)
        (finally 
          (.close iterator)
          (-> data-store .getFeatureReader .close)
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defn kml->conditions-vec
  "Converts a kml file to a vector of SpatialConditions"
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          input-stream (FileInputStream. file)
          parser (PullParser. (KMLConfiguration.) input-stream SimpleFeature)
          feature-list (ArrayList.)]
      (try
        (util/while-let [feature (.parse parser)]
          (when (> (feature-point-count feature) 0)
            (.add feature-list feature)))
        (features->conditions feature-list mt/kml)
        (finally
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defn in-memory->conditions-vec
  "Converts a group of features produced by simplification to a vector of SpatialConditions"
  [shapefile-info]
  (let [^ArrayList features (:tempfile shapefile-info)
        mime-type (:content-type shapefile-info)]
    (features->conditions features mime-type)))

(defmulti shapefile->conditions
  "Converts a shapefile to query conditions based on shapefile format"
  (fn [shapefile-info]
    (info (format "SHAPEFILE FORMAT: %s" (:contenty-type shapefile-info)))
    (if (:in-memory shapefile-info)
      :in-memory
      (:content-type shapefile-info))))

;; ESRI shapefiles
(defmethod shapefile->conditions mt/shapefile
  [shapefile-info]
  (let [conditions-vec (esri-shapefile->condition-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; GeoJSON
(defmethod shapefile->conditions mt/geojson
  [shapefile-info]
  (let [conditions-vec (geojson->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; KML
(defmethod shapefile->conditions mt/kml
  [shapefile-info]
  (let [conditions-vec (kml->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

;; Simplfied and stored in memory
(defmethod shapefile->conditions :in-memory
  [shapefile-info]
  (let [conditions-vec (in-memory->conditions-vec shapefile-info)]
    (gc/or-conds (flatten conditions-vec))))

(defmethod p/parameter->condition :shapefile
  [_context _concept-type _param value _options]
  (if (enable-shapefile-parameter-flag)
    (shapefile->conditions value)
    (errors/throw-service-error :bad-request "Searching by shapefile is not enabled")))

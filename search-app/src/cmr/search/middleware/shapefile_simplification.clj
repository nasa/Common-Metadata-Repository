(ns cmr.search.middleware.shapefile-simplification
  "Middleware to optionally reduce the complexity of shapefiles"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer [debug error]]
   [cmr.common.mime-types :as mt]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as p]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as qm]
   [cmr.search.services.parameters.converters.geojson :as geojson]
   [cmr.search.services.parameters.converters.geometry :as geo]
   [cmr.search.services.parameters.converters.shapefile :as shapefile]
   [cmr.common.util :as util])
  (:import
   (java.io BufferedInputStream File FileReader FileOutputStream FileInputStream)
   (java.nio.file Files)
   (java.nio.file.attribute FileAttribute)
   (java.net URL)
   (java.util ArrayList HashMap)
   (java.util.zip ZipFile ZipInputStream ZipOutputStream ZipEntry)
   (org.apache.commons.io FilenameUtils)
   (org.geotools.data DataStoreFinder FileDataStoreFinder Query)
   (org.geotools.data.collection ListFeatureCollection)
   (org.geotools.data.shapefile ShapefileDumper)
   (org.geotools.data.simple SimpleFeatureSource)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.feature.simple SimpleFeatureBuilder)
  ;  (org.geotools.geojson.feature FeatureJSON)
   (org.geotools.geometry.jts JTS)
   (org.geotools.kml.v22 KMLConfiguration KML)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)
   (org.geotools.xsd Encoder Parser StreamingParser PullParser)
   (org.locationtech.jts.geom Geometry)
   (org.opengis.feature.simple SimpleFeature)
   (org.opengis.feature.type FeatureType Name)
   (org.locationtech.jts.simplify TopologyPreservingSimplifier)))

(def SHAPEFILE_SIMPLIFICATION_HEADER "CMR-Shapfile-Simplification")

(def EPSG-4326-CRS
  "The CRS object for WGS 84"
  (CRS/decode "EPSG:4326" true))

(defconfig max-shapefile-features
  "The maximum number of feature a shapefile can have"
  {:default 500 :type Long})

(defconfig max-shapefile-points
  "The maximum number of points a shapefile can have"
  {:default 5000 :type Long})

(defn delete-recursively
  "Delete a (possibly non-empty) directory"
  [fname]
  (let [func (fn [func f]
               (when (.isDirectory f)
                 (doseq [f2 (.listFiles f)]
                   (func func f2)))
               (clojure.java.io/delete-file f))]
    (func func (clojure.java.io/file fname))))

(defn- zip-dir
  "Create a zip file with the contents of a directory"
  [dir-path output-path]
  (with-open [zip (ZipOutputStream. (io/output-stream output-path))]
    (doseq [f (file-seq (io/file dir-path)) :when (.isFile f)]
      (.putNextEntry zip (ZipEntry. (.getName f)))
      (io/copy f zip)
      (.closeEntry zip))))

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

(defn geometry-point-count
  "Get the number of points in the given Geometry"
  [^Geometry geometry]
  (let [num-geometries (.getNumGeometries geometry)
        all-geometries  (for [index (range 0 num-geometries)
                              :let [sub-geometry (.getGeometryN geometry index)]]
                          sub-geometry)]
    (reduce (fn [count geometry] (+ count (.getNumPoints geometry))) 0 all-geometries)))

(defn- feature-point-count
  "Get the number of points in the given Feature"
  [feature]
  (let [properties (.getProperties feature)
        geometry-props (filter (fn [p] (geo/geometry? (.getValue p))) properties)]
    (apply + (map #(geometry-point-count (.getValue %)) geometry-props))))

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

(defn- simplify-geometry
  "Simplify a geometry. Returns simplfied geometry and information about the simplificiton"
  [geometry tolerance]
  (when (> (.getNumGeometries geometry) 1)
    (println "MULTIGEOMETRY=========================="))
  (println (format "POINT COUNT %d" (geometry-point-count geometry)))
  (let [new-geometry (TopologyPreservingSimplifier/simplify geometry tolerance)]
    (println (format "NEW POINT COUNT %d" (geometry-point-count new-geometry)))
    (println (format "NUMBER OF GEOMETRIES: %d" (.getNumGeometries new-geometry)))
    (println new-geometry)
    new-geometry))

(defn- simplify-feature
  "Simplify a feature. Returns simplfied feature and information about the simplificiton"
  [feature tolerance]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        feature-type (.getFeatureType feature)
        ; _ (println "FEATURE TYPE++++++++++++++++++")
        ; _ (println feature-type)
        feature-builder (SimpleFeatureBuilder. feature-type)
        ; _ (println "CREATED FEATURE BUILDER+++++++++++++++++++++++")
        properties (.getProperties feature)
        _ (println (format "FOUND %d PROPERTIES" (count properties)))
        ; _ (doseq [p properties] (println (.getName p)) (println (.getValue p)))
        _ (doseq [p properties
                  :let [value  (.getValue p)]]
            (if (geo/geometry? value)
              (do
                (println "FOUND GEOMETRY PROPERTY")
                (.add feature-builder (simplify-geometry (transform-to-epsg-4326 value crs) tolerance)))
              (.add feature-builder value)))]
    (.buildFeature feature-builder nil)))

(defn- error-if
  "Throw a service error with the given message if `f` applied to `item` is true. 
  Otherwise just return `item`. Removes the temporary file/directory `temp-file` first."
  [item f message ^File temp-file]
  (if (f item)
    (do
      (when temp-file (.delete temp-file))
      (errors/throw-service-error :bad-request message))
    item))

(defn- simplify-features
  "Simplify a collection of features and return a collection of the simplified features
  as well as the original and new point counts"
  [features tolerance]
  (let [feature-count (error-if (.size features) #(< % 1) "Shapefile has no features" nil)
        feature-list (ArrayList.)]
    (println (format "Found [%d] features" feature-count))
    (let [[old-total-point-count new-total-point-count]
          (reduce (fn [old-val feature]
                    (let [[old-total-point-count new-total-point-count] old-val
                          old-point-count (feature-point-count feature)
                          new-feature (simplify-feature feature tolerance)
                          new-point-count (feature-point-count new-feature)]
                      (.add feature-list new-feature)
                      [(+ old-total-point-count old-point-count)
                       (+ new-total-point-count new-point-count)]))
                  [0 0]
                  features)]
      (println (format "Original point count: %d" old-total-point-count))
      (println (format "New point count: %d" new-total-point-count))
      [feature-list [old-total-point-count new-total-point-count]])))

(defn- simplify-data
  "Given an ArrayList of Features simplify them, write out a simplified shapefie, then
  return information about the shapefile and stats about the simplification"
  [filename ^ArrayList features feature-type ^Float tolerance]
  (let [target-dir (Files/createTempDirectory "Shapes" (into-array FileAttribute []))]
    (try
      (let [new-file (java.io.File/createTempFile "reduced" ".zip")
            dumper (ShapefileDumper. (.toFile target-dir))
            [simplified-features stats] (simplify-features features tolerance)
            [original-point-count new-point-count] stats
            collection (ListFeatureCollection. feature-type simplified-features)]
        (.dump dumper collection)
        (zip-dir (.toString target-dir) (.toString new-file))
        ;; TODO Remove next line after debugging
        (io/copy new-file (File. "/tmp/shapefile.zip"))
        [{:original-point-count original-point-count
          :new-point-count new-point-count}
         {:tempfile new-file
          :filename filename
          :content-type mt/shapefile
          :size (.length new-file)}])
      (finally (delete-recursively (.toString target-dir))))))

(defn simplify-geojson
  "Simplfies a geojson file. Returns statistics about the simplification and information
  about the new file."
  [shapefile-info tolerance]
  (try
    (let [file (:tempfile shapefile-info)
          filename (:filename shapefile-info)
          ; _ (.setMaxDbfSize dumper 100000000)
          _ (geojson/sanitize-geojson file)
          url (URLs/fileToUrl file)
          data-store (GeoJSONDataStore. url)
          feature-source (.getFeatureSource data-store)
          feature-type (.getSchema feature-source)
          features (.getFeatures feature-source)
          feature-count (error-if (.size features)
                                  #(< % 1)
                                  "GeoJSON has no features"
                                  nil)
          _ (println (format "Found [%d] features" feature-count))
          iterator (.features features)]
      (try
        (loop [features (ArrayList.)]
          (if (.hasNext iterator)
            (let [feature (.next iterator)]
              (.add features feature)
              (recur features))
            (simplify-data filename features feature-type tolerance)))
        (finally (do
                   ;  (.close output-stream)

                   (.close iterator)
                   (-> data-store .getFeatureReader .close)
                   (.delete file)
                   (println "COMPLETED SIMPLIFICATION++++++++++++++++++")))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (.printStackTrace e)
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse GeoJSON file"))))))

(defmulti simplify
  "Simplifies a shapefile and returns the shapefile info"
  (fn [shapefile-info]
    (:content-type shapefile-info)))

;; GeoJSON
(defmethod simplify mt/geojson
  [shapefile-info]
  (simplify-geojson shapefile-info 0.1))

(defn- simplify-shapefile
  "Simplifies the shapefile indicated in the parameters and updates the parameters
  to point to the new (reduced) shapefile"
  [request]
  (println "REQUEST++++++++++++++++++++++++++++++++++++++")
  (println request)
  (if (= "true" (get-in request [:params "simplify-shapefile"]))
    (if-let [tmp-file (get-in request [:params "shapefile" :tempfile])]
      (do
        (println "SIMPLIFYING SHAPEFILE-------------------------------")
        (let [[stats result] (simplify (get-in request [:params "shapefile"]))
              header (json/generate-string stats)]
          [result header]))
      (do
        (println "MISSING SHAPEFILE-------------------------------")
        (errors/throw-service-error :bad-request "Missing shapefile")))
    (println "NOT SIMPLIFYING SHAPEFILE------------------------")))

(defn shapefile-simplifier
  "Adds shapefile simplification header to response when shapefile simplication was
  requested."
  [handler default-format-fn]
  (fn [{context :request-context :as request}]
    (try
      (if-let [[result header] (simplify-shapefile request)]
        (-> (assoc-in request [:params "shapefile"] result)
            (handler)
            (assoc-in [:headers SHAPEFILE_SIMPLIFICATION_HEADER] header))
        (do
          (println "UNHANDLED++++++++++++++++++++++")
          (handler request)))
      (catch Exception e
        (let [{:keys [type errors]} (ex-data e)]
          (if (= type :bad-request)
            (api-errors/handle-service-error
             default-format-fn request type errors e)
                ;; re-throw non-service errors
            (throw e)))))))


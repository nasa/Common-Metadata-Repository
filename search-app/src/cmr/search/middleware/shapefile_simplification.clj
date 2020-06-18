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
   (org.locationtech.jts.geom GeometryFactory Geometry Polygon LinearRing LineString MultiPolygon)
   (org.opengis.feature.simple SimpleFeature)
   (org.opengis.feature.type FeatureType Name)
   (org.locationtech.jts.simplify TopologyPreservingSimplifier)))

(def SHAPEFILE_SIMPLIFICATION_HEADER "CMR-Shapfile-Simplification")

(defconfig shapefile-simplifier-start-tolerance
  "The tolerance to use for the first pass at shapefile simplification"
  {:default 0.1 :type Double})

(defconfig shapefile-simplifier-max-attempts
  "The maximum number of times to attempt to simplify a shapefile"
  {:default 5 :type Long})

(defn- delete-recursively
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
           (not (= (.getName src-crs) (.getName shapefile/EPSG-4326-CRS))))
    (let [src-crs-name (.getName src-crs)]
      (debug (format "Source CRS: [%s]" src-crs-name))
      (debug (format "Source axis order: [%s]" (CRS/getAxisOrder src-crs)))
      (debug (format "Destination CRS: [%s]" (.getName shapefile/EPSG-4326-CRS)))
      (debug (format "Destination axis order: [%s]" (CRS/getAxisOrder shapefile/EPSG-4326-CRS)))
      ; If we find a transform use it to transform the geometry, 
      ; otherwise send an error message
      (if-let [transform (try
                           (CRS/findMathTransform src-crs shapefile/EPSG-4326-CRS false)
                           (catch Exception e))]
        (let [new-geometry (JTS/transform geometry transform)]
          (debug (format "New geometry: [%s" new-geometry))
          new-geometry)
        (errors/throw-service-error :bad-request (format "Cannot transform source CRS [%s] to WGS 84" src-crs-name))))
    geometry))

(defn- winding-opts
  "Get the opts for a call to `normalize-polygon-winding` based on file type"
  [mime-type]
  (case mime-type
    "application/shapefile+zip" {:boundary-winding :cw}
    "application/vnd.google-earth.kml+xml" {}
    "application/geo+json" {:hole-winding :cw}))

(defn- normalize-polygon-winding
  "Force CCW winding for outer rings and CW winding for inner rings (holes)"
  [^Polygon polygon options]
  (let [geometry-factory (.getFactory polygon)
        ^LinearRing boundary-ring (.getExteriorRing polygon)
        _ (debug (format "BOUNDARY RING BEFORE FORCE-CCW: %s" boundary-ring))
        boundary-ring (geo/force-ccw-orientation boundary-ring (:boundary-winding options))
        _ (debug (format "BOUNDARY RING AFTER FORCE-CCW: %s" boundary-ring))
        num-interior-rings (.getNumInteriorRing polygon)
        interior-rings (if (> num-interior-rings 0)
                         (for [i (range num-interior-rings)]
                           (geo/force-ccw-orientation (.getInteriorRingN polygon i)
                                                      (:hole-winding options)))
                         [])
        all-rings (concat [boundary-ring] interior-rings)]
    (debug (format "NUM INTERIOR RINGS: [%d]" num-interior-rings))
    (debug (format "RINGS: [%s]" (vec all-rings)))
    (let [holes (into-array LinearRing interior-rings)]
      (Polygon. boundary-ring holes geometry-factory))))

(defn- normalize-geometry
  "Normalize the windings on polygons - other geometries are untouched"
  [^Geometry geometry  mime-type]
  (let [geometry-type (.getGeometryType geometry)
        geometry-factory (.getFactory geometry)
        options (winding-opts mime-type)]
    (if (= geometry-type "MultiPolygon")
      (let [num-polys (.getNumGeometries geometry)
            normalized-polys  (for [index (range 0 num-polys)
                                    :let [sub-geometry (.getGeometryN geometry index)]]
                                (normalize-polygon-winding sub-geometry options))]
        (MultiPolygon. (into-array Polygon normalized-polys) geometry-factory))
      (if (= geometry-type "Polygon")
        (normalize-polygon-winding geometry options)
        geometry))))

(defn- simplify-geometry
  "Simplify a geometry"
  [geometry tolerance mime-type]
  ; (when (> (.getNumGeometries geometry) 1)
  ;   (println "MULTIGEOMETRY=========================="))
  ; (println (format "POINT COUNT %d" (geometry-point-count geometry)))
  (let [normalized-geometry (normalize-geometry geometry mime-type)
        new-geometry (TopologyPreservingSimplifier/simplify normalized-geometry tolerance)]
    ; (println (format "NEW POINT COUNT %d" (geometry-point-count new-geometry)))
    ; (println (format "NUMBER OF GEOMETRIES: %d" (.getNumGeometries new-geometry)))
    ; (println new-geometry)
    new-geometry))

(defn- simplify-feature
  "Simplify a feature. Returns simplfied feature and information about the simplificiton"
  [feature tolerance mime-type]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        feature-type (.getFeatureType feature)
        ; _ (println "FEATURE TYPE++++++++++++++++++")
        ; _ (println feature-type)
        feature-builder (SimpleFeatureBuilder. feature-type)
        ; _ (println "CREATED FEATURE BUILDER+++++++++++++++++++++++")
        properties (.getProperties feature)
        ; _ (println (format "FOUND %d PROPERTIES" (count properties)))
        ; _ (doseq [p properties] (println (.getName p)) (println (.getValue p)))
        _ (doseq [p properties
                  :let [value  (.getValue p)]]
            (if (geo/geometry? value)
              (do
                ; (println "FOUND GEOMETRY PROPERTY")
                (.add feature-builder (simplify-geometry (transform-to-epsg-4326 value crs) 
                                                         tolerance 
                                                         mime-type)))
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
  [features tolerance mime-type]
  (let [feature-count (error-if (.size features) #(< % 1) "Shapefile has no features" nil)
        feature-list (ArrayList.)]
    (println (format "Found [%d] features" feature-count))
    (println (format "TOLERANCE: %f" tolerance))
    (let [[old-total-point-count new-total-point-count]
          (reduce (fn [old-val feature]
                    (let [[old-total-point-count new-total-point-count] old-val
                          old-point-count (feature-point-count feature)
                          new-feature (simplify-feature feature tolerance mime-type)
                          new-point-count (feature-point-count new-feature)]
                      (.add feature-list new-feature)
                      [(+ old-total-point-count old-point-count)
                       (+ new-total-point-count new-point-count)]))
                  [0 0]
                  features)]
      ; (println (format "Original point count: %d" old-total-point-count))
      ; (println (format "New point count: %d" new-total-point-count))
      [feature-list [old-total-point-count new-total-point-count]])))

(defn- iterative-simplify
  "Repeatedly simplify a list of Features until the number of points is below the
  CMR shapefile point limit"
  [features mime-type]
  (let [limit (shapefile/max-shapefile-points)
        start-tolerance (shapefile-simplifier-start-tolerance)
        start-point-count (reduce (fn [total feature] (+ total (feature-point-count feature)))
                                  0
                                  features)]
    (loop [tolerance start-tolerance
           result [features [start-point-count start-point-count]]
           count 1]
      (let [[new-features [old-count new-count]] result]
        (println (format "POINT COUNTS: [%d %d]" old-count new-count))
        (if (<= new-count limit)
          result
          (if (> count (shapefile-simplifier-max-attempts))
            (errors/throw-service-error :bad-request "Shapefile could not be simplified")
            (recur (* tolerance 10.0)
                   (simplify-features new-features tolerance mime-type) (inc count))))))))

(defn- simplify-data
  "Given an ArrayList of Features simplify the Features and return stats about the process
  along with a new shapfile info map to replace the one in the search reqeust"
  [filename ^ArrayList features feature-type mime-type]
  (let [; new-file (java.io.File/createTempFile "reduced" ".zip")
        ; dumper (ShapefileDumper. (.toFile target-dir))
        ; _ (println features)
        [simplified-features stats] (iterative-simplify features mime-type)
        ; _ (println simplified-features)
        [original-point-count new-point-count] stats]
        ; collection (ListFeatureCollection. feature-type simplified-features)]
    ; (.dump dumper collection)
    ; (zip-dir (.toString target-dir) (.toString new-file))
    [{:original-point-count original-point-count
      :new-point-count new-point-count}
     {:tempfile simplified-features
      :filename filename
      :content-type mime-type
      :in-memory true
      :was-simplified (not (= original-point-count new-point-count))
      :size -1}]))

(defn- simplify-kml
  "Simplfies a KML file. Returns statistics about the simplification and information
  about the new file."
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          filename (:filename shapefile-info)
          input-stream (FileInputStream. file)
          parser (PullParser. (KMLConfiguration.) input-stream SimpleFeature)]
      (try
        (loop [features (ArrayList.)]
          (if-let [feature (.parse parser)]
            (do
              ; (println "FEATURE++++++++++++++++")
              ; (println feature)
              (.add features feature)
              (recur features))
            (if-let [first-feature (when (not (.isEmpty features)) (.get features 0))]
              (simplify-data filename features (.getFeatureType first-feature) mt/kml)
              (errors/throw-service-error :bad-request "KML file has no features"))))
        (finally
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (do
            (.printStackTrace e)
            (errors/throw-service-error :bad-request "Failed to parse KML file")))))))

(defn- simplify-geojson
  "Simplfies a geojson file. Returns statistics about the simplification and information
  about the new file."
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          filename (:filename shapefile-info)
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
          iterator (.features features)]
      (try
        (loop [features (ArrayList.)]
          (if (.hasNext iterator)
            (let [feature (.next iterator)]
              (.add features feature)
              (recur features))
            (simplify-data filename features feature-type mt/geojson)))
        (finally (do
                   (.close iterator)
                   (-> data-store .getFeatureReader .close)
                   (.delete file)))))
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
  (simplify-geojson shapefile-info))

(defmethod simplify mt/kml
  [shapefile-info]
  (simplify-kml shapefile-info))

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


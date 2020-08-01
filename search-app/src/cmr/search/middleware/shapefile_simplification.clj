(ns cmr.search.middleware.shapefile-simplification
  "Middleware to optionally reduce the complexity of shapefiles"
  (:require
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.api.errors :as api-errors]
   [cmr.common.log :refer [debug]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.search.services.parameters.converters.geojson :as geojson]
   [cmr.search.services.parameters.converters.geometry :as geo]
   [cmr.search.services.parameters.converters.shapefile :as shapefile]
   [cmr.common.util :as util])
  (:import
   (java.io File FileInputStream)
   (java.nio.file Files)
   (java.net URL)
   (java.util ArrayList)
   (org.geotools.data DataStoreFinder FileDataStoreFinder)
   (org.geotools.data.geojson GeoJSONDataStore)
   (org.geotools.feature.simple SimpleFeatureBuilder)
   (org.geotools.geometry.jts JTS)
   (org.geotools.kml.v22 KMLConfiguration KML)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)
   (org.geotools.xsd Encoder Parser StreamingParser PullParser)
   (org.locationtech.jts.geom GeometryFactory Geometry Polygon LinearRing LineString MultiPolygon)
   (org.opengis.feature.simple SimpleFeature)
   (org.opengis.feature.type FeatureType Name)
   (org.locationtech.jts.simplify TopologyPreservingSimplifier)))

(def SHAPEFILE_SIMPLIFIED_COUNT_HEADER "CMR-Shapefile-Simplified-Point-Count")
(def SHAPEFILE_ORIGINAL_COUNT_HEADER "CMR-Shapefile-Original-Point-Count")

(defconfig shapefile-simplifier-start-tolerance
  "The tolerance to use for the first pass at shapefile simplification"
  {:default 0.1 :type Double})

(defconfig shapefile-simplifier-max-attempts
  "The maximum number of times to attempt to simplify a shapefile"
  {:default 5 :type Long})

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

(defn- simplify-geometry
  "Simplify a geometry"
  [geometry tolerance mime-type]
  (TopologyPreservingSimplifier/simplify geometry tolerance))

(defn- simplify-feature
  "Simplify a feature. Returns simplified feature and information about the simplification"
  [feature tolerance mime-type]
  (let [crs (when (.getDefaultGeometryProperty feature)
              (-> feature .getDefaultGeometryProperty .getDescriptor .getCoordinateReferenceSystem))
        feature-type (.getFeatureType feature)
        feature-builder (SimpleFeatureBuilder. feature-type)
        properties (.getProperties feature)]
    (doseq [p properties
            :let [value  (.getValue p)]]
      (if (geo/geometry? value)
        (.add feature-builder (simplify-geometry (shapefile/transform-to-epsg-4326 value crs)
                                                 tolerance
                                                 mime-type))
        (.add feature-builder value)))
    (.buildFeature feature-builder nil)))

(defn- simplify-features
  "Simplify a collection of features and return a collection of the simplified features
  as well as the original and new point counts"
  [features tolerance mime-type]
  (let [feature-count (shapefile/error-if (.size features) #(< % 1) "Shapefile has no features" nil)
        feature-list (ArrayList.)]
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
        (if (<= new-count limit)
          result
          (if (> count (shapefile-simplifier-max-attempts))
            (errors/throw-service-error :bad-request "Shapefile could not be simplified")
            (recur (* tolerance 10.0)
                   (simplify-features new-features tolerance mime-type) (inc count))))))))

(defn- simplify-data
  "Given an ArrayList of Features simplify the Features and return stats about the process
  along with a new shapefile info map to replace the one in the search reqeust"
  [filename ^ArrayList features mime-type]
  (let [[simplified-features stats] (iterative-simplify features mime-type)
        [original-point-count new-point-count] stats]
    [[original-point-count
      new-point-count]
     {:tempfile simplified-features
      :filename filename
      :content-type mime-type
      :in-memory true
      :was-simplified (not (= original-point-count new-point-count))
      :size -1}]))

(defn- simplify-esri
  "Simplifies an ESRI shapefile. Returns statistics about the simplification and information
  about the new file."
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          filename (:filename shapefile-info)
          ^File temp-dir (shapefile/unzip-file file)
          shp-file (shapefile/error-if
                    (shapefile/find-shp-file temp-dir)
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
            (simplify-data filename feature-list mt/shapefile)
        (finally
          (.close iterator)
          (-> data-store .getFeatureReader .close)
          (.delete temp-dir)
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse shapefile"))))))

(defn- simplify-kml
  "Simplfies a KML file. Returns statistics about the simplification and information
  about the new file."
  [shapefile-info]
  (try
    (let [file (:tempfile shapefile-info)
          filename (:filename shapefile-info)
          input-stream (FileInputStream. file)
          parser (PullParser. (KMLConfiguration.) input-stream SimpleFeature)
          feature-list (ArrayList.)]
      (try
        (util/while-let [feature (.parse parser)]
          (when (> (feature-point-count feature) 0)
            (.add feature-list feature)))
        (simplify-data filename feature-list mt/kml)
        (finally
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse KML file"))))))

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
          features (.getFeatures feature-source)
          iterator (.features features)
          feature-list (ArrayList.)]
      (try
        (while (.hasNext iterator)
          (let [feature (.next iterator)]
            (.add feature-list feature)))
        (simplify-data filename feature-list mt/geojson)
        (finally
          (.close iterator)
          (-> data-store .getFeatureReader .close)
          (.delete file))))
    (catch Exception e
      (let [{:keys [type errors]} (ex-data e)]
        (if (and type errors)
          (throw e) ;; This was a more specific service error so just re-throw it
          (errors/throw-service-error :bad-request "Failed to parse GeoJSON file"))))))

(defmulti simplify
  "Simplifies a shapefile and returns the shapefile info"
  (fn [shapefile-info]
    (:content-type shapefile-info)))

(defmethod simplify mt/shapefile
  [shapefile-info]
  (simplify-esri shapefile-info))

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
  (when (= "true" (get-in request [:params "simplify-shapefile"]))
    (if (get-in request [:params "shapefile" :tempfile])
      (simplify (get-in request [:params "shapefile"]))
      (errors/throw-service-error :bad-request "Missing shapefile"))))

(defn shapefile-simplifier
  "Adds shapefile simplification header to response when shapefile simplication was
  requested."
  [handler default-format-fn]
  (fn [{_context :request-context :as request}]
    (try
      (if-let [[[original-point-count new-point-count] result] (simplify-shapefile request)]
        (-> (assoc-in request [:params "shapefile"] result)
            (handler)
            (assoc-in [:headers SHAPEFILE_ORIGINAL_COUNT_HEADER] (str original-point-count))
            (assoc-in [:headers SHAPEFILE_SIMPLIFIED_COUNT_HEADER] (str new-point-count)))
        (handler request))
      (catch Exception e
        (let [{:keys [type errors]} (ex-data e)]
          (if (= type :bad-request)
            (api-errors/handle-service-error
             default-format-fn request type errors e)
                ;; re-throw non-service errors
            (throw e)))))))


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
   (org.geotools.kml.v22 KMLConfiguration KML)
   (org.geotools.referencing CRS)
   (org.geotools.util URLs)
   (org.geotools.xsd Parser StreamingParser PullParser)
   (org.opengis.feature.simple SimpleFeature)
   (org.opengis.feature.type Name)))

(def SHAPEFILE_SIMPLIFICATION_HEADER "CMR-Shapfile-Simplification")

(defn- simplify-shapefile
  "Simplifies the shapefile indicated in the parameters and updates the parameters
  to point to the new (reduced) shapefile"
  [request]
  (println "REQUEST++++++++++++++++++++++++++++++++++++++")
  (println request)
  (when (= "true" (get-in request [:params "simplify-shapefile"]))
    (if-let [tmp-file (get-in request [:params :shapefile :tempfile])]
      (do
        (println "SIMPLIFYING SHAPEFILE-------------------------------")
        (json/generate-string {:original-vertex-count 100
                               :reduced-vertex-count 50}))
      (errors/throw-service-error :bad-request "Missing shapefile"))))

(defn shapefile-simplifier
  "Adds shapefile simplification header to response when shapefile simplication was
  requested."
  [handler default-format-fn]
  (fn [{context :request-context :as request}]
    (try
      (if-let [shapefile-simplification-info (simplify-shapefile request)]
        (-> request
            (handler)
            (assoc-in [:headers SHAPEFILE_SIMPLIFICATION_HEADER] shapefile-simplification-info))
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


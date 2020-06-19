(ns cmr.search.middleware.shapefile
  "Contains parameter converters for shapefile parameter"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
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

(defconfig max-shapefile-size
  "The maximum size in bytes a shapefile can be"
  {:default 1000000 :type Long})

(defn- progress
  "Progress function for `wrap-multipart-params`. This function simply throws an error if
  the uploaded file exceeds a given limit."
  [_request _bytes-read content-length _item-count]
  (when (> content-length (max-shapefile-size))
    (error (format "Failed shapefile upload of size [%d] bytes" content-length))
    (errors/throw-service-error :bad-request
                                (format "Shapefile size exceeds the %d byte limit" (max-shapefile-size)))))

(defn shapefile-upload
  "Middleware to handle shapefile uploads"
  [handler default-format-fn]
  (fn [request]
    (try
      ((wrap-multipart-params handler {:progress-fn progress}) request)
      (catch Exception e
        (let [{:keys [type errors]} (ex-data e)]
          (if (= type :bad-request)
            (api-errors/handle-service-error
             default-format-fn request type errors e)
            ;; re-throw non-service errors
            (throw e)))))))

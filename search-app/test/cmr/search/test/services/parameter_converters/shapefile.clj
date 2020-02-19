(ns cmr.search.test.services.parameter-converters.shapefile
  (:require 
    [clojure.test :refer :all]
    [clj-time.core :as t]
    [cmr.search.services.parameters.converters.shapefile :as shapefile]
    [cmr.search.services.messages.attribute-messages :as msg]
    [cmr.search.models.query :as qm]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.params :as p]
    [cmr.common.util :as u])
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
    (org.geotools.geometry.jts JTS JTSFactoryFinder)
    (org.geotools.referencing CRS)
    (org.geotools.util URLs)
    (org.locationtech.jts.geom GeometryFactory Coordinate  Point)))

(defn expected-error
  "Creates an expected error response"
  [f & args]
  {:errors [(apply f args)]})


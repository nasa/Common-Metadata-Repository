(ns cmr.search.test.services.parameter-converters.shapefile
  (:require 
    [clj-time.core :as t]
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
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


(deftest shapefile-exceptions
  (testing "empty file"
    (try
      (let [shp-file (io/file (io/resource "shapefiles/empty.zip"))]
        (shapefile/esri-shapefile->condition-vec {:tempfile shp-file}))
      (catch Exception e
        (is (= "Shapefile has no features"
               (.getMessage e))))))

  (testing "corrupt zip file"
    (try
      (let [shp-file (io/file (io/resource "shapefiles/corrupt_file.zip"))]
        (shapefile/esri-shapefile->condition-vec {:tempfile shp-file}))
      (catch Exception e
        (is (= "Error while uncompressing zip file: invalid END header (bad central directory offset)"
               (.getMessage e))))))

  (testing "corrupt zip file"
    (try
      (let [shp-file (io/file (io/resource "shapefiles/missing_shapefile_shp.zip"))]
        (shapefile/esri-shapefile->condition-vec {:tempfile shp-file}))
      (catch Exception e
        (is (= "Incomplete shapefile: missing .shp file"
               (.getMessage e)))))))
               

;; See https://epsg.io/transform and https://spatialreference.org/ for test cases and independently 
;; verified transform values

(deftest transform-crs
  (let [^GeometryFactory geometry-factory (JTSFactoryFinder/getGeometryFactory)
        ^Coordinate coord (Coordinate. 10 32)
        ^Point point (.createPoint geometry-factory coord)]

    (testing "failure case - World Equidistant Cylindrical (Sphere)"
      (let [crs (CRS/decode "EPSG:3786" true)] 
        (try
          (shapefile/transform-to-epsg-4326 point crs)
          (catch Exception e
            (is (= (.getMessage e) 
                 "Cannot transform source CRS [EPSG:World Equidistant Cylindrical (Sphere)] to WGS 84"))))))
          
    (testing "successful cases"
      (are3 [crs-code force-lon-first coords]
        (let [crs (CRS/decode crs-code force-lon-first)
              transformed-point (shapefile/transform-to-epsg-4326 point crs)
              x (.getX transformed-point)
              y (.getY transformed-point)]
          (is (= coords [x y])))
        
        "Pseudo Mercator"
        "EPSG:3857" true [8.983152841195215E-5 2.874608909118022E-4]
        
        "Arctic"
        "EPSG:6125" false [-90.0927568294979 -64.17838193594663]
        
        "UTM ZONE 11N"
        "EPSG:2955" false [-121.48866759617566 2.8851809782082726E-4]))))

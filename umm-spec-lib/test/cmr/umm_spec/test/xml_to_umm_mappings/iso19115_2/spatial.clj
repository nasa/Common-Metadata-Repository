(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.spatial
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common.xml.simple-xpath :as xpath]
   [cmr.umm-spec.xml-to-umm-mappings.iso19115-2.spatial :as spatial]))

(deftest test-iso19115-2-spatial-xml-to-umm-conversion
  (testing "test the defmacro to see if it creates the correct map"

    (are3 [expected]
      (is (= expected (spatial/horizontal-resolution-code-name-to-key-map)))
      "Test the defmacro to see if it creates the correct map"
      {"variesresolution" :VariesResolution,
       "NonGriddedResolutions" :NonGriddedResolutions,
       "NonGriddedRangeResolutions" :NonGriddedRangeResolutions,
       "GenericResolutions" :GenericResolutions,
       "nongriddedresolutions" :NonGriddedResolutions,
       "pointresolution" :PointResolution,
       "PointResolution" :PointResolution,
       "GriddedResolutions" :GriddedResolutions,
       "nongriddedrangeresolutions" :NonGriddedRangeResolutions,
       "VariesResolution" :VariesResolution,
       "GriddedRangeResolutions" :GriddedRangeResolutions,
       "genericresolutions" :GenericResolutions,
       "griddedresolutions" :GriddedResolutions,
       "griddedrangeresolutions" :GriddedRangeResolutions})))

(deftest test-coordinate-system-parsing
  "Testing the parsing of the spatial coordinate system. The values of CARTESIAN and GEODETIC
   are tested with other tests. Data that contains EPSG will be tested here."

  (testing "test the parsing of the spatial coordinate system.")
  (let [value (-> (slurp (io/resource "example-data/iso19115/artificial_test_data_2.xml"))
                  (string/replace #">GEODETIC<" ">urn:ogc:def:crs:EPSG::4326<")
                  (xpath/context)
                  (spatial/parse-coordinate-system))]
    (is (= "GEODETIC" value))))

(deftest test-coordinate-system-parsing-large-num
  "Testing the ability to handle parsing a number that exceeds the max integer value"

  (let [value (spatial/parse-horizontal-data-resolutions
               (slurp (io/resource "example-data/iso19115/artificial_test_data_large_num.xml")))]
    (is (= 2147483648 (-> value :GriddedRangeResolutions first :MaximumYDimension)))))


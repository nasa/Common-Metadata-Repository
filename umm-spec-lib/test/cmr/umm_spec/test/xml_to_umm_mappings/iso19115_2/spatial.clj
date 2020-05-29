(ns cmr.umm-spec.test.xml-to-umm-mappings.iso19115-2.spatial
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.common.xml.simple-xpath :refer [select text]]
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

(ns cmr.umm-spec.test.metadata-specification
  "Functions related to the MetadataSpecification node common to many of the umm
   models. Eventually all models will include this node, so a common set of
   functions is needed."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.umm-spec.versioning :as ver]
   [cmr.umm-spec.metadata-specification :as m-spec]))

(deftest spec-content-test
  (testing
    "Test the utility function that builds the MetadataSpecification structure"
    (let [expected {:URL "https://cdn.earthdata.nasa.gov/umm/granule/v1.2.3",
                    :Name "UMM-G",
                    :Version "1.2.3"}
          actual (m-spec/metadata-spec-content :granule "1.2.3")]
      (is (= expected actual))))
  (testing
    "Test all the supported formats"
    (are3 [format version expected-name expected-url]
          (let [content (m-spec/metadata-spec-content format version)]
            (is (= expected-name (:Name content)))
            (is (clojure.string/ends-with? (:URL content) expected-url))
            (is (= version (:Version content))))

          "Collection"
          :collection "0.1" "UMM-C" "/umm/collection/v0.1"

          "Granule"
          :granule "0.1" "UMM-G" "/umm/granule/v0.1"

          "Service"
          :service "0.1" "UMM-S" "/umm/service/v0.1"

          "Tool"
          :tool "0.1" "UMM-T" "/umm/tool/v0.1"

          "Variable"
          :variable "0.1" "UMM-Var" "/umm/variable/v0.1"

          "Visualization"
          :visualization "0.1" "UMM-Vis" "/umm/visualization/v0.1")))

(deftest update-version-test
  "Test the update-version function and make sure it can update metadata as expected"
  (testing
    "Populate MetadataSpecification if missing"
    (let [expected {:MetadataSpecification (m-spec/metadata-spec-content :granule "0.1")}
          actual (m-spec/update-version {} :granule "0.1")]
      (is (= expected actual))))
  (testing
    "Do not overwrite unrelated data while updating"
    (let [expected {:SomeMetaValue "Science Happens Here"
                    :MetadataSpecification (m-spec/metadata-spec-content :granule "0.1")}
          metadata {:SomeMetaValue "Science Happens Here"
                    :MetadataSpecification {:URL "old value"
                                            :Name "UMM-G"
                                            :Version "1.0"}}
          actual (m-spec/update-version metadata :granule "0.1")]
      (is (= expected actual)))))

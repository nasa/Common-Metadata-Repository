(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as dtp]
   [cmr.common.test.test-check-ext :as ext :refer [checking checking-with-seed]]
   [cmr.common.util :as util]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.umm-spec.test.umm-g.expected-util :as expected-util]
   [cmr.umm-spec.test.umm-g.generators :as generators]
   [cmr.umm-spec.test.umm-g.sanitizer :as sanitizer]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-lib-g]
   [cmr.umm.umm-spatial :as umm-s]))

(deftest generate-granule-is-valid-umm-g-test
  (checking "umm-g validate metadata" 100
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          metadata (core/generate-metadata {} granule :umm-json)]
      (is (empty? (core/validate-metadata :granule :umm-json metadata))))))

(deftest generate-and-parse-umm-g-granule-test
  (checking "umm-g round tripping" 100
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          umm-g-metadata (core/generate-metadata {} granule :umm-json)
          actual (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          expected (expected-util/umm->expected-parsed granule)
          actual (update-in parsed [:spatial-coverage :geometries] set)
          expected (update-in expected [:spatial-coverage :geometries] set)]
      (is (= expected actual)))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.4/GranuleExample.json"))))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-util/expected-sample-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))

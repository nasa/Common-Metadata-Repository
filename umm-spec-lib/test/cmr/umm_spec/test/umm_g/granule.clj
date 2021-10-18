(ns cmr.umm-spec.test.umm-g.granule
  "Tests parsing and generating UMM-G granule."
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [cmr.common.test.test-check-ext :as ext :refer [checking checking-with-seed]]
   [cmr.umm-spec.test.umm-g.expected-util :as expected-util]
   [cmr.umm-spec.test.umm-g.generators :as generators]
   [cmr.umm-spec.test.umm-g.sanitizer :as sanitizer]
   [cmr.umm-spec.umm-spec-core :as core]))

(deftest generate-granule-is-valid-umm-g-test
  (checking "umm-g validate metadata" 100
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          metadata (core/generate-metadata {} granule :umm-json)]
      (is (empty? (core/validate-metadata :granule :umm-json metadata))))))

(deftest generate-granule-is-valid-umm-g-test-with-seed
  (checking-with-seed "umm-g validate metadata" 100 1634099338048
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          metadata (core/generate-metadata {} granule :umm-json)]
      (is (empty? (core/validate-metadata :granule :umm-json metadata))))))

(deftest generate-and-parse-umm-g-granule-test-seed
  (checking-with-seed "umm-g round tripping" 100 1601556014491
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          umm-g-metadata (core/generate-metadata {} granule :umm-json)
          actual (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          expected (expected-util/umm->expected-parsed granule)
          actual (update-in actual [:spatial-coverage :geometries] set)]
      (is (= expected actual)))))

(deftest generate-and-parse-umm-g-granule-test
  (checking "umm-g round tripping" 100
    [granule (gen/no-shrink generators/umm-g-granules)]
    (let [granule (sanitizer/sanitize-granule granule)
          umm-g-metadata (core/generate-metadata {} granule :umm-json)
          actual (core/parse-metadata {} :granule :umm-json umm-g-metadata)
          expected (expected-util/umm->expected-parsed granule)
          actual (update-in actual [:spatial-coverage :geometries] set)]
      (is (= expected actual)))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.6/GranuleExample.json"))))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-util/expected-sample-granule
           (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))

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

(defn- apply-updates-to-expected 
  "Apply updates to the expected umm-lib granule."
  [expected-umm]
  (-> expected-umm
      (update-in [:spatial-coverage :geometries] set)
      ;; Need to remove the possible duplicate entries in crid-ids and feature-ids
      ;; because Identifiers in UMM-G can't contain any duplicates.
      (as-> updated-umm (if (get-in updated-umm [:data-granule :crid-ids])
                          (update-in updated-umm [:data-granule :crid-ids] distinct)
                          updated-umm))
      (as-> updated-umm (if (get-in updated-umm [:data-granule :feature-ids])
                          (update-in updated-umm [:data-granule :feature-ids] distinct)
                          updated-umm))))

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
          actual (update-in actual [:spatial-coverage :geometries] set)
          expected (apply-updates-to-expected expected)]
      (is (= expected actual)))))

(def sample-umm-g-granule
  (slurp (io/file (io/resource "example-data/umm-json/granule/v1.4/GranuleExample.json"))))

(deftest parse-granule-test
  (testing "parse granule"
    (is (= expected-util/expected-sample-granule (core/parse-metadata {} :granule :umm-json sample-umm-g-granule)))))

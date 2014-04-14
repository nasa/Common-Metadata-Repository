(ns cmr.common.test.concepts
  (:require [clojure.test :refer :all]

            ; [clojure.test.check.clojure-test :refer [defspec]]
            ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
            [cmr.common.test.test-check-ext :refer [defspec]]

            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [cmr.common.concepts :as c]))

(deftest parse-concept-id-test
  (testing "parse collection id"
    (is (= {:concept-type :collection
            :sequence-number 12
            :provider-id "PROV_A42"}
           (c/parse-concept-id "C12-PROV_A42"))))
  (testing "parse granule id"
    (is (= {:concept-type :granule
            :sequence-number 12
            :provider-id "PROV_A42"}
           (c/parse-concept-id "G12-PROV_A42"))))
  (testing "parse invalid concept id"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Concept-id \[.*\] is not valid."
                          (c/parse-concept-id "G5-PROV1;")))))

(def concept-id-maps
  "A generator for concept id maps"
  (gen/hash-map :concept-type (gen/elements (vec c/concept-types))
                :sequence-number gen/s-pos-int
                :provider-id (gen/not-empty gen/string-alpha-numeric)))

;; Tests that all generated concept id maps can converted to concept ids and parsed.
(defspec parse-build-concept-id-test 100
  (for-all [concept-id-map concept-id-maps]
    (let [concept-id (c/build-concept-id concept-id-map)]
      (= concept-id-map (c/parse-concept-id concept-id)))))
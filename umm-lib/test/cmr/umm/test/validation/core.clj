(ns cmr.umm.test.validation.core
  "This has tests for UMM validations."
  (:require [clojure.test :refer :all]
            [cmr.umm.validation.core :as v]
            [cmr.umm.collection :as c]))


(defn assert-valid
  "Asserts that the given umm model is valid."
  [umm]
  (is (empty? (v/validate :echo10 umm))))

(defn assert-invalid
  "Asserts that the given umm model is invalid and has the expected error messages."
  [umm metadata-format expected-errors]
  (is (= expected-errors (v/validate metadata-format umm))))

(defn coll-with-psas
  [psas]
  (c/map->UmmCollection {:product-specific-attributes psas}))

(deftest collection-product-specific-attributes-validation
  (testing "valid product specific attributes"
    (assert-valid (coll-with-psas [{:name "foo"} {:name "bar"}])))

  (testing "invalid product specific attributes"
    (testing "duplicate names"
      (let [coll (coll-with-psas [{:name "foo"} {:name "foo"} {:name "bar"} {:name "bar"}
                                  {:name "charlie"}])]
        (assert-invalid
          coll :echo10
          ["AdditionalAttributes must be unique. This contains duplicates named [foo, bar]."])
        (assert-invalid
          coll :dif
          ["AdditionalAttributes must be unique. This contains duplicates named [foo, bar]."])))))

(deftest collection-projects-validation
  (let [c1 (c/map->Project {:short-name "C1"})
        c2 (c/map->Project {:short-name "C2"})
        c3 (c/map->Project {:short-name "C3"})]
    (testing "valid projects"
      (assert-valid (c/map->UmmCollection {:projects [c1 c2]})))

    (testing "invalid projects"
      (testing "duplicate names"
        (let [coll (c/map->UmmCollection {:projects [c1 c1 c2 c2 c3]})]
          (assert-invalid
            coll :echo10
            ["Campaigns must be unique. This contains duplicates named [C1, C2]."])
          (assert-invalid
            coll :dif
            ["Project must be unique. This contains duplicates named [C1, C2]."])
          (assert-invalid
            coll :iso19115
            ["MI_Metadata/acquisitionInformation/MI_AcquisitionInformation/operation/MI_Operation must be unique. This contains duplicates named [C1, C2]."]))))))


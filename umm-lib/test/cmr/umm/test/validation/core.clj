(ns cmr.umm.test.validation.core
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
          ["AdditionalAttributes must be unique. It contained duplicate names [foo, bar]."])
        (assert-invalid
          coll :dif
          ["AdditionalAttributes must be unique. It contained duplicate names [foo, bar]."])))))
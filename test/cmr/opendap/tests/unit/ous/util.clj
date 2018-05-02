(ns cmr.opendap.tests.unit.ous.util
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.util :as util]))

(deftest bounding-box->subset)

(deftest coverage->granules
  (is (= nil
         (util/coverage->granules
          [])))
  (is (= nil
         (util/coverage->granules
          [""])))
  (is (= ["G123"]
         (util/coverage->granules
          ["G123"])))
  (is (= ["G123" "G234"]
         (util/coverage->granules
          ["G123" "G234"])))
  (is (= ["G123" "G234"]
         (util/coverage->granules
          ["C012" "G123" "G234"]))))

(deftest coverage->collection
  (is (= nil
         (util/coverage->collection
          [])))
  (is (= nil
         (util/coverage->collection
          [""])))
  (is (= nil
         (util/coverage->collection
          ["G123"])))
  (is (= nil
         (util/coverage->collection
          ["G123" "G234"])))
  (is (= "C012"
         (util/coverage->collection
          ["C012" "G123" "G234"]))))

(deftest subset->bounding-box
  (is (= [-9.984375 56.109375 19.828125 67.640625]
         (util/subset->bounding-box
          ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]))))

(deftest bounding-box->subset
  (is (= ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]
         (util/bounding-box->subset
          [-9.984375 56.109375 19.828125 67.640625]))))

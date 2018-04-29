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

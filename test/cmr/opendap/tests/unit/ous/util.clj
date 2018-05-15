(ns cmr.opendap.tests.unit.ous.util
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.util :as util]
    [ring.util.codec :as codec]))

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
          [-9.984375 56.109375 19.828125 67.640625])))
  (is (= ["lat(63.5625,66.09375)" "lon(-23.0625,57.09375)"]
         (util/bounding-box->subset
          [-23.0625 63.5625 57.09375 66.09375]))))

(deftest temporal-seq->cmr-query
  (let [result (util/temporal-seq->cmr-query
                ["2002-09-01T00:00:00Z" "2016-07-03T00:00:00Z"])]
    (is (= "temporal%5B%5D=2002-09-01T00%3A00%3A00Z&temporal%5B%5D=2016-07-03T00%3A00%3A00Z"
           result))
    (is (= "temporal[]=2002-09-01T00:00:00Z&temporal[]=2016-07-03T00:00:00Z"
           (codec/url-decode result)))))

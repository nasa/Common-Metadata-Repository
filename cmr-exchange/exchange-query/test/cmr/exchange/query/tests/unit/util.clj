(ns cmr.exchange.query.tests.unit.util
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.exchange.query.impl.wcs :as wcs]
    [cmr.exchange.query.impl.cmr :as cmr]
    [cmr.exchange.query.util :as util]
    [ring.util.codec :as codec]))

(deftest ->coll
  (is (= [] (util/->coll nil)))
  (is (= [] (util/->coll "")))
  (is (= [] (util/->coll [])))
  (is (= [:c :b :a] (util/->coll [:c :b :a])))
  (is (= ["Stuff"] (util/->coll "Stuff"))))

(deftest split-comma->coll
  (is (= [] (util/split-comma->coll nil)))
  (is (= [] (util/split-comma->coll "")))
  (is (= [] (util/split-comma->coll [])))
  (is (= [:c :b :a] (util/split-comma->coll [:c :b :a])))
  (is (= ["Stuff"] (util/split-comma->coll "Stuff")))
  (is (= ["Stuff" "N" "Things"] (util/split-comma->coll "Stuff,N,Things"))))

(deftest split-comma->sorted-coll
  (is (= [] (util/split-comma->sorted-coll nil)))
  (is (= [] (util/split-comma->sorted-coll "")))
  (is (= [] (util/split-comma->sorted-coll [])))
  (is (= [:a :b :c] (util/split-comma->sorted-coll [:c :b :a])))
  (is (= ["Stuff"] (util/split-comma->sorted-coll "Stuff")))
  (is (= ["N" "Stuff" "Things"] (util/split-comma->sorted-coll "Stuff,N,Things"))))

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

(deftest unique-params-keys
  (is (= #{:coverage :rangesubset :timeposition}
         (util/unique-params-keys wcs/map->CollectionWcsStyleParams)))
  (is (= #{:exclude-granules :variables :granules :bounding-box :temporal :service-id}
         (util/unique-params-keys cmr/map->CollectionCmrStyleParams))))

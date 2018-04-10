(ns cmr.opendap.tests.unit.ous.collection
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.collection :as collection]))

(deftest params-keys
  (is (= #{:coverage :rangesubset}
         collection/ous-prototype-params-keys))
  (is (= #{:exclude-granules :variables :granules :bounding-box}
         collection/collection-params-keys)))

(deftest params?
  (testing "For records ..."
    (is (collection/ous-prototype-params?
         (collection/map->OusPrototypeParams {})))
    (is (not (collection/ous-prototype-params?
              (collection/map->CollectionParams {}))))
    (is (not (collection/collection-params?
              (collection/map->OusPrototypeParams {}))))
    (is (collection/collection-params?
         (collection/map->CollectionParams {}))))
  (testing "For collection-params URL query strings ..."
    (is (collection/collection-params?
         {:granules "G1200187775-EDF_OPS,G1200245955-EDF_OPS"}))
    (is (collection/collection-params?
         {:granules "G1200187775-EDF_OPS,G1200245955-EDF_OPS"
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (collection/ous-prototype-params?
          {:granules "G1200187775-EDF_OPS,G1200245955-EDF_OPS"})))
    (is (not
         (collection/ous-prototype-params?
          {:granules "G1200187775-EDF_OPS,G1200245955-EDF_OPS"
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))))
  (testing "For ous-prototype-params URL query strings ..."
    (is (collection/ous-prototype-params?
         {:coverage "G1200187775-EDF_OPS,G1200245955-EDF_OPS"}))
    (is (collection/ous-prototype-params?
         {:coverage "G1200187775-EDF_OPS,G1200245955-EDF_OPS"
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (collection/collection-params?
          {:coverage "G1200187775-EDF_OPS,G1200245955-EDF_OPS"})))
    (is (not
         (collection/collection-params?
          {:coverage "G1200187775-EDF_OPS,G1200245955-EDF_OPS"
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]})))))

(ns cmr.opendap.tests.unit.ous.collection.params
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.collection.params.core :as params]
    [cmr.opendap.ous.collection.params.v1 :as v1]
    [cmr.opendap.ous.collection.params.v2 :as v2]))

(deftest params-keys
  (is (= #{:coverage :rangesubset}
         v1/params-keys))
  (is (= #{:exclude-granules :variables :granules :bounding-box}
         v2/params-keys)))

(deftest params?
  (testing "For records ..."
    (is (v1/params?
         (v1/map->OusPrototypeParams {})))
    (is (not (v1/params?
              (v2/map->CollectionParams {}))))
    (is (not (v2/params?
              (v1/map->OusPrototypeParams {}))))
    (is (v2/params?
         (v2/map->CollectionParams {}))))
  (testing "For collection-params URL query strings ..."
    (is (v2/params?
         {:granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]}))
    (is (v2/params?
         {:granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (v1/params?
          {:granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]})))
    (is (not
         (v1/params?
          {:granules ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))))
  (testing "For ous-prototype-params URL query strings ..."
    (is (v1/params?
         {:coverage ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]}))
    (is (v1/params?
         {:coverage "G1200187775-EDF_OPS,G1200245955-EDF_OPS"
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (v2/params?
          {:coverage ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]})))
    (is (not
         (v2/params?
          {:coverage ["G1200187775-EDF_OPS" "G1200245955-EDF_OPS"]
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]})))))

(deftest v1->v2
  (is (= (v2/map->CollectionParams {:exclude-granules false})
         (params/v1->v2
          (v1/map->OusPrototypeParams {}))))
  (is (= (v2/map->CollectionParams
          {:format "nc"
           :collection-id "C123"
           :granules ["G234" "G345" "G456"]
           :variables ["V234" "V345" "V456"]
           :subset ["lat(22,34)" "lon(169,200)"]
           :exclude-granules false})
         (params/v1->v2
          (v1/map->OusPrototypeParams {
           :format "nc"
           :coverage ["C123"
                      "G234"
                      "G345"
                      "G456"]
           :rangesubset ["V234" "V345" "V456"]
           :subset ["lat(22,34)" "lon(169,200)"]})))))

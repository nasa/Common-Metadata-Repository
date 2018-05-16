(ns cmr.opendap.tests.unit.ous.query.params
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.query.params.core :as params]
    [cmr.opendap.ous.query.params.v1 :as v1]
    [cmr.opendap.ous.query.params.v2 :as v2]))

(deftest params-keys
  (is (= #{:coverage :rangesubset :timeposition}
         v1/params-keys))
  (is (= #{:exclude-granules :variables :granules :bounding-box :temporal}
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
         {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
    (is (v2/params?
         {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (v1/params?
          {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]})))
    (is (not
         (v1/params?
          {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))))
  (testing "For ous-prototype-params URL query strings ..."
    (is (v1/params?
         {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
    (is (v1/params?
         {:coverage "G1200267320-HMR_TME,G1200267319-HMR_TME"
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (v2/params?
          {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]})))
    (is (not
         (v2/params?
          {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
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
           :exclude-granules false
           :bounding-box [169.0 22.0 200.0 34.0]
           :temporal ["2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"]})
         (params/v1->v2
          (v1/map->OusPrototypeParams {
           :format "nc"
           :coverage ["C123"
                      "G234"
                      "G345"
                      "G456"]
           :rangesubset ["V234" "V345" "V456"]
           :subset ["lat(22,34)" "lon(169,200)"]
           :timeposition ["2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"]})))))

(ns cmr.opendap.tests.unit.ous.query.params
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.query.params.core :as params]
    [cmr.opendap.ous.query.params.wcs :as wcs]
    [cmr.opendap.ous.query.params.cmr :as cmr])
  (:refer-clojure :exclude [parse]))

(deftest params-keys
  (is (= #{:coverage :rangesubset :timeposition}
         wcs/params-keys))
  (is (= #{:exclude-granules :variables :granules :bounding-box :temporal}
         cmr/params-keys)))

(deftest params?
  (testing "For records ..."
    (is (wcs/params?
         (wcs/map->CollectionWcsStyleParams {})))
    (is (not (wcs/params?
              (cmr/map->CollectionCmrStyleParams {}))))
    (is (not (cmr/params?
              (wcs/map->CollectionWcsStyleParams {}))))
    (is (cmr/params?
         (cmr/map->CollectionCmrStyleParams {}))))
  (testing "For collection-params URL query strings ..."
    (is (cmr/params?
         {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
    (is (cmr/params?
         {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (wcs/params?
          {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]})))
    (is (not
         (wcs/params?
          {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))))
  (testing "For ous-prototype-params URL query strings ..."
    (is (wcs/params?
         {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
    (is (wcs/params?
         {:coverage "G1200267320-HMR_TME,G1200267319-HMR_TME"
          :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
    (is (not
         (cmr/params?
          {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]})))
    (is (not
         (cmr/params?
          {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
           :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]})))))

(deftest wcs->cmr
  (is (= (cmr/map->CollectionCmrStyleParams {:exclude-granules false})
         (params/wcs->cmr
          (wcs/map->CollectionWcsStyleParams {}))))
  (is (= (cmr/map->CollectionCmrStyleParams
          {:format "nc"
           :collection-id "C123"
           :granules ["G234" "G345" "G456"]
           :variables ["V234" "V345" "V456"]
           :exclude-granules false
           :bounding-box [169.0 22.0 200.0 34.0]
           :subset ["lat(22,34)" "lon(169,200)"]
           :temporal "2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"})
         (params/wcs->cmr
          (wcs/map->CollectionWcsStyleParams {
           :format "nc"
           :coverage ["C123"
                      "G234"
                      "G345"
                      "G456"]
           :rangesubset ["V234" "V345" "V456"]
           :subset ["lat(22,34)" "lon(169,200)"]
           :timeposition "2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"}))))
  (testing "Temporal variations ..."
    (is (= (cmr/map->CollectionCmrStyleParams
            {:collection-id "C123"
             :granules ["G234" "G345" "G456"]
             :temporal ["2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"]
             :exclude-granules false})
           (params/wcs->cmr
            (wcs/map->CollectionWcsStyleParams {
             :coverage ["C123"
                        "G234"
                        "G345"
                        "G456"]
             :timeposition ["2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"]}))))
    (is (= (cmr/map->CollectionCmrStyleParams
            {:collection-id "C123"
             :granules ["G234" "G345" "G456"]
             :temporal ["2000-01-01T00:00:00Z,2002-10-01T00:00:00Z"
                        "2010-07-01T00:00:00Z,2016-07-03T00:00:00Z"]
             :exclude-granules false})
           (params/wcs->cmr
            (wcs/map->CollectionWcsStyleParams {
             :coverage ["C123"
                        "G234"
                        "G345"
                        "G456"]
             :timeposition ["2000-01-01T00:00:00Z,2002-10-01T00:00:00Z"
                            "2010-07-01T00:00:00Z,2016-07-03T00:00:00Z"]}))))))

(deftest parse
  (is (= (cmr/map->CollectionCmrStyleParams
          {:collection-id nil
           :format "nc"
           :granules []
           :exclude-granules false
           :variables ["V234" "V345"]
           :subset nil
           :bounding-box nil
           :temporal []})
         (params/parse {:variables ["V234" "V345"]})))
  (is (= {:errors ["One or more of the parameters provided were invalid." "Parameters: {:blurg \"some weird data\"}"]}
         (params/parse {:blurg "some weird data"}))))

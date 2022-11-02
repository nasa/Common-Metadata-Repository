(ns cmr.exchange.query.tests.unit.core
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [clojure.string :as string]
    [cmr.exchange.query.core :as query]
    [cmr.exchange.query.impl.cmr :as cmr]
    [cmr.exchange.query.impl.giovanni :as giovanni]
    [cmr.exchange.query.impl.wcs :as wcs]
    [ring.util.codec :as codec])
  (:refer-clojure :exclude [parse]))

(defn- decode-and-split-as-set
  [query]
  (-> query
      codec/url-decode
      (string/split #"&")
      set))

(deftest cmr-style?
  (is (cmr/style?
       {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
  (is (cmr/style?
       {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
        :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
  (is (cmr/style?
       {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
  (is (cmr/style?
       {:granules ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
        :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]})))

(deftest wcs-style?
  (is (wcs/style?
       {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
  (is (wcs/style?
       {:coverage "G1200267320-HMR_TME,G1200267319-HMR_TME"
        :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]}))
  (is (wcs/style?
       {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]}))
  (is (wcs/style?
       {:coverage ["G1200267320-HMR_TME" "G1200267319-HMR_TME"]
        :subset ["lat(56.109375,67.640625)" "lon(-9.984375,19.828125)"]})))

(deftest giovanni-style?
  (is (giovanni/style?
       {:bbox [169.0 22.0 200.0 34.0]})))

(deftest ->query-string
  (testing "CMR ->query-string ..."
    (is (= (decode-and-split-as-set
            (query/->query-string
             (cmr/map->CollectionCmrStyleParams
              {:format "nc"
               :collection-id "C123"
               :granules ["G234" "G345" "G456"]
               :variables ["V234" "V345" "V456"]
               :exclude-granules false
               :bounding-box [169.0 22.0 200.0 34.0]
               :subset ["lat(22,34)" "lon(169,200)"]
               :temporal "2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"})))
           #{"format=nc"
             "collection-id=C123"
             "granules=G234"
             "granules=G345"
             "granules=G456"
             "variables=V234"
             "variables=V345"
             "variables=V456"
             "exclude-granules=false"
             "bounding-box=169.0,22.0,200.0,34.0"
             "subset=lat(22,34)"
             "subset=lon(169,200)"
             "temporal=2002-09-01T00:00:00Z,2016-07-03T00:00:00Z"})))
  (testing "Cmr ->query-string with empty and nil values ..."
    (is (= (decode-and-split-as-set
            (query/->query-string
             (cmr/map->CollectionCmrStyleParams
              {:format nil
               :collection-id "C123"
               :granules []
               :variables []
               :bounding-box []}))))
        #{"colleciton-id=C123"}))
  (testing "Giovanni ->query-string ..."
    (is (= (decode-and-split-as-set
            (query/->query-string
             (giovanni/map->CollectionGiovanniStyleParams
              {:bbox [-180 -90 180 90]
               :data ["data0" "data1" "data2"]})))
           #{"bbox=-180,-90,180,90"
             "data=data0"
             "data=data1"
             "data=data2"})))
  (testing "WCS ->query-string ..."
    (is (= (decode-and-split-as-set
            (query/->query-string
             (wcs/map->CollectionWcsStyleParams
              {:coverage ["coverage0" "coverage1"]
               :timeposition "2001-01-01T00:00:00"})))
           #{"coverage=coverage0"
             "coverage=coverage1"
             "timeposition=2001-01-01T00:00:00"}))))

(deftest ->cmr
  (is (= (cmr/map->CollectionCmrStyleParams {:exclude-granules false})
         (query/->cmr
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
         (query/->cmr
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
           (query/->cmr
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
           (query/->cmr
            (wcs/map->CollectionWcsStyleParams {
             :coverage ["C123"
                        "G234"
                        "G345"
                        "G456"]
             :timeposition ["2000-01-01T00:00:00Z,2002-10-01T00:00:00Z"
                            "2010-07-01T00:00:00Z,2016-07-03T00:00:00Z"]}))))))

(deftest cmr-create
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :collection-id "C130"
          :format "nc"
          :granules []
          :exclude-granules false
          :variables ["V234" "V345"]
          :subset nil
          :bounding-box nil
          :temporal []})
         (cmr/create {:collection-id "C130" :variables ["V234" "V345"]}))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :collection-id "C130"
          :format "nc"
          :granules []
          :exclude-granules false
          :variables ["V234" "V345"]
          :subset nil
          :bounding-box nil
          :temporal []})
         (query/parse {:collection-id "C130" "variables[]" ["V234" "V345"]})))

(deftest parse
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :bounding-box nil
          :collection-id "C130"
          :exclude-granules false
          :format nil
          :granules ()
          :subset nil
          :temporal []
          :variables ()}
         (query/parse {:collection-id "C130"})))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :bounding-box nil
          :collection-id "C130"
          :exclude-granules false
          :format nil
          :granules ()
          :subset []
          :temporal []
          :variables ()}
         (query/parse {:collection-id "C130" :subset []})))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :collection-id "C130"
          :format "nc"
          :granules []
          :exclude-granules false
          :variables ["V234" "V345"]
          :subset nil
          :bounding-box nil
          :temporal []}
         (query/parse {:format "nc" :collection-id "C130" :variables ["V234" "V345"]})))
  (is (= #cmr.exchange.query.impl.cmr.CollectionCmrStyleParams{
          :collection-id "C130"
          :format "nc"
          :granules []
          :exclude-granules false
          :variables ["V234" "V345"]
          :subset nil
          :bounding-box nil
          :temporal []}
         (query/parse {:format "nc" :collection-id "C130" "variables[]" ["V234" "V345"]})))
  (is (= {:errors ["The following required parameters are missing from the request: [:collection-id]"]}
         (query/parse {:variables ["V234" "V345"]}
                      nil
                      {:required-params #{:collection-id}})))
  (is (= {:errors ["One or more of the parameters provided were invalid."
                   "Parameters: {:collection-id \"C130\", :blurg \"some weird data\"}"]}
         (query/parse {:collection-id "C130" :blurg "some weird data"}))))

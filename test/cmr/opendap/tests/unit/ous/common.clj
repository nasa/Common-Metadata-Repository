(ns cmr.opendap.tests.unit.ous.common
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [clojusc.twig :as logger]
    [cmr.opendap.ous.common :as common]
    [cmr.opendap.ous.variable :as variable]))

(logger/set-level! '[] :fatal)

(def collection {:dataset_id "Aqua AIRS Level 3 Daily Standard Physical Retrieval (AIRS+AMSU) V006 (AIRX3STD) at GES DISC"})

(deftest bounding-infos->opendap-query
  (let [dims (array-map :Latitude 180 :Longitude 360)]
    (testing "No bounds, Latitude & Longitude ..."
     (is (= "?MyVar,Latitude,Longitude"
            (common/bounding-infos->opendap-query
             collection
             [{:name "MyVar"
               :dimensions dims
               :original-dimensions dims}])))
     (is (= "?MyVar1,MyVar2,Latitude,Longitude"
            (common/bounding-infos->opendap-query
             collection
             [{:name "MyVar1"
               :dimensions dims
               :original-dimensions dims}
              {:name "MyVar2"
               :dimensions dims
               :original-dimensions dims}]))))
    (testing "With bounds, Latitude & Longitude ..."
      (let [bounds [-27.421875 53.296875 18.5625 69.75]
            bounding-info [{:name "MyVar"
                            :bounds bounds
                            :dimensions dims
                            :original-dimensions dims
                            :opendap (variable/create-opendap-bounds
                                      dims bounds {:reversed? true})}]]
       (is (= "?MyVar[20:1:37][152:1:199],Latitude[20:1:37],Longitude[152:1:199]"
              (common/bounding-infos->opendap-query
               collection bounding-info bounds))))))
  (let [dims (array-map :Latitude 180 :Longitude 360)
        orig-dims (array-map :lat 180 :lon 360)]
    (testing "No bounds, lat & lon ..."
     (is (= "?MyVar,lat,lon"
            (common/bounding-infos->opendap-query
             collection
             [{:name "MyVar"
               :dimensions dims
               :original-dimensions orig-dims}])))
     (is (= "?MyVar1,MyVar2,lat,lon"
            (common/bounding-infos->opendap-query
             collection
             [{:name "MyVar1"
               :dimensions dims
               :original-dimensions orig-dims}
              {:name "MyVar2"
               :dimensions dims
               :original-dimensions orig-dims}]))))
    (testing "With bounds, lat & lon ..."
      (let [bounds [-27.421875 53.296875 18.5625 69.75]
            bounding-info [{:name "MyVar"
                            :bounds bounds
                            :dimensions dims
                            :original-dimensions orig-dims
                            :opendap (variable/create-opendap-bounds
                                      dims bounds {:reversed? true})}]]
       (is (= "?MyVar[20:1:37][152:1:199],lat[20:1:37],lon[152:1:199]"
              (common/bounding-infos->opendap-query
               collection bounding-info bounds)))))))

;;; UMM-Var Test data

(def xdim-v1-1 {:Name "XDim" :Size 360})
(def xdim-v1-2 {:Name "XDim" :Size 360 :Type "LONGITUDE_DIMENSION"})
(def ydim-v1-1 {:Name "YDim" :Size 180})
(def ydim-v1-2 {:Name "YDim" :Size 180 :Type "LATITUDE_DIMENSION"})
(def dims-v1-1 [xdim-v1-1 ydim-v1-1])
(def dims-v1-2 [xdim-v1-2 ydim-v1-2])

(deftest lat-dim?
  (is (not (common/lat-dim? ydim-v1-1)))
  (is (common/lat-dim? ydim-v1-2)))

(deftest lon-dim?
  (is (not (common/lon-dim? xdim-v1-1)))
  (is (common/lon-dim? xdim-v1-2)))

(deftest gridded-dim?
  (is (not (common/gridded-dim? xdim-v1-1)))
  (is (common/gridded-dim? xdim-v1-2))
  (is (not (common/gridded-dim? ydim-v1-1)))
  (is (common/gridded-dim? ydim-v1-2)))

(deftest gridded-dims?
  (is (not (common/gridded-dims? dims-v1-1)))
  (is (common/gridded-dims? dims-v1-2)))

(deftest gridded-vars?
  (let [v1-1-vars [{:umm {:Dimensions dims-v1-1}}
                   {:umm {:Dimensions dims-v1-1}}]
        v1-2-vars [{:umm {:Dimensions dims-v1-2}}
                   {:umm {:Dimensions dims-v1-2}}]
        v1-2-vars-partial-1 [{:umm {:Dimensions [xdim-v1-2]}}]
        v1-2-vars-partial-2 [{:umm {:Dimensions [ydim-v1-2]}}]]
    (is (not (common/gridded-vars? v1-1-vars)))
    (is (common/gridded-vars? v1-2-vars))
    (is (not (common/gridded-vars? v1-2-vars-partial-1)))
    (is (not (common/gridded-vars? v1-2-vars-partial-2)))))

(deftest strip-spatial
  (let [stripped (common/strip-spatial {:temporal :thing
                                        :bounding-box [-15.46875 30.375 4.359375 43.03125]
                                        :subset ["lat(30.375,43.03125)" "lon(-15.46875,4.359375)"]})]
    (is (= [] (:bounding-box stripped)))
    (is (= [] (:subset stripped)))
    (is (not (= [] (:temporal stripped))))))

(ns cmr.ous.tests.system.util.geog
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.ous.util.geog :as geog]
    [cmr.ous.testing.config :as test-system]))

(use-fixtures :once test-system/with-system)

(def lat-lon-dims
  {:umm
    {:Dimensions
      [{:Name "EmisFreqIR"
        :Size 4}
       {:Name "Latitude"
        :Size 130}
       {:Name "Longitude"
        :Size 270}]}})

(def lat-lon-dims-mixed-order
  {:umm
    {:Dimensions
      [{:Name "Longitude"
        :Size 270}
       {:Name "EmisFreqIR"
        :Size 4}
       {:Name "Latitude"
        :Size 130}]}})

(def x-y-dims
  {:umm
    {:Dimensions
      [{:Name "EmisFreqIR"
        :Size 4}
       {:Name "YDim"
        :Size 100}
       {:Name "XDim"
        :Size 200}]}})

(def no-spatial-dims
  {:umm
    {:Dimensions
      [{:Name "EmisFreqIR"
        :Size 4}]}})

(def ummvar-1-2-dims
  {:umm
    {:Dimensions
      [{:Name "time"
        :Size 1
        :Type "OTHER"}
       {:Name "lat"
        :Size 17999
        :Type "LATITUDE_DIMENSION"}
       {:Name "lon"
        :Size 36000
        :Type "LONGITUDE_DIMENSION"}]}})

(def ummvar-1-2-index-ranges
  {:umm
    {:Characteristics
      {:IndexRanges
        {:LatRange [-90 90]
         :LonRange [-180 180]}}}})

(def ummvar-1-2-index-ranges-reversed
  {:umm
    {:Characteristics
      {:IndexRanges
        {:LatRange [90 -90]
         :LonRange [-180 180]}}}})

(def ummvar-1-7-index-ranges
  {:umm
   {:IndexRanges
     {:LatRange [-90 90]
      :LonRange [-180 180]}}})

(def ummvar-1-7-index-ranges-reversed
  {:umm
   {:IndexRanges
     {:LatRange [90 -90]
      :LonRange [-180 180]}}})

(deftest lon-dim
  (is (nil? (geog/lon-dim (geog/extract-dimensions no-spatial-dims))))
  (is (= {:Size 270, :Name :Longitude, :Type nil}
         (geog/lon-dim (geog/extract-dimensions lat-lon-dims))))
  (is (= {:Size 270, :Name :Longitude, :Type nil}
         (geog/lon-dim (geog/extract-dimensions lat-lon-dims-mixed-order))))
  (is (= {:Size 200, :Name :XDim, :Type nil}
         (geog/lon-dim (geog/extract-dimensions x-y-dims))))
  (is (= {:Size 36000, :Name :lon, :Type :LONGITUDE_DIMENSION}
         (geog/lon-dim (geog/extract-dimensions ummvar-1-2-dims)))))

(deftest extract-dimensions
  (let [dims (geog/extract-dimensions lat-lon-dims)]
    (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
            :Latitude {:Size 130 :Type nil :Name :Latitude}
            :Longitude {:Size 270 :Type nil :Name :Longitude}}
           dims))
    (is (= [:EmisFreqIR :Latitude :Longitude] (keys dims))))
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
          :YDim {:Size 100 :Type nil :Name :YDim}
          :XDim {:Size 200 :Type nil :Name :XDim}}
         (geog/extract-dimensions x-y-dims)))
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}}
         (geog/extract-dimensions no-spatial-dims)))
  (let [dims (geog/extract-dimensions lat-lon-dims-mixed-order)]
    (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
            :Latitude {:Size 130 :Type nil :Name :Latitude}
            :Longitude {:Size 270 :Type nil :Name :Longitude}}
           dims))
    (is (= [:Longitude :EmisFreqIR :Latitude] (keys dims)))))

(deftest normalize-lat-lon
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
          :Latitude {:Size 130 :Type nil :Name :Latitude}
          :Longitude {:Size 270 :Type nil :Name :Longitude}}
         (geog/normalize-lat-lon
          (geog/extract-dimensions lat-lon-dims))))
  (is (= {:EmisFreqIR {:Name :EmisFreqIR :Size 4 :Type nil}
          :Latitude {:Name :YDim :Size 100 :Type nil}
          :Longitude {:Name :XDim :Size 200 :Type nil}}
         (geog/normalize-lat-lon
          (geog/extract-dimensions x-y-dims))))
  (is (= {:Latitude {:Name :lat :Size 17999 :Type :LATITUDE_DIMENSION}
          :Longitude {:Name :lon :Size 36000 :Type :LONGITUDE_DIMENSION}
          :time {:Name :time :Size 1 :Type :OTHER}}
         (geog/normalize-lat-lon
          (geog/extract-dimensions ummvar-1-2-dims))))
  (is (= {:EmisFreqIR {:Name :EmisFreqIR :Size 4 :Type nil}
          :Latitude nil
          :Longitude nil}
         (geog/normalize-lat-lon
          (geog/extract-dimensions no-spatial-dims)))))

(deftest extract-indexranges
  (testing "Index ranges indicating non-reversed latitudinal values ..."
    (is (= {:low {:lon -180 :lat -90}
            :high {:lon 180 :lat 90}
            :lat-reversed? false}
           (into {} (geog/extract-indexranges ummvar-1-7-index-ranges)))))
  (testing "Index ranges indicating reversed latitudinal values ..."
    (is (= {:low {:lon -180 :lat -90}
            :high {:lon 180 :lat 90}
            :lat-reversed? true}
           (into {} (geog/extract-indexranges ummvar-1-7-index-ranges-reversed))))))

(deftest create-opendap-bounds
  (let [dims (array-map :Longitude {:Size 360} :Latitude {:Size 180})
        bounds [-27.421875 53.296875 18.5625 69.75]
        lookup-array (geog/create-opendap-bounds
                      dims bounds {:reversed? true})]
    (is (= 152 (get-in lookup-array [:low :lon])))
    (is (= 20 (get-in lookup-array [:low :lat])))
    (is (= 199 (get-in lookup-array [:high :lon])))
    (is (= 37 (get-in lookup-array [:high :lat])))))

(deftest format-opendap-bounds-no-lat-lon
  (is (= "MyVar"
         (geog/format-opendap-bounds {:name "MyVar"}))))

(deftest format-opendap-bounds-lat-lon-only
  (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (geog/create-opendap-bounds
                                 dims bounds {:reversed? true})}]
    (is (= "MyVar[22:1:34][169:1:200]"
           (geog/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[20:1:37][152:1:199]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[23:1:27][156:1:237]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[21:1:65][156:1:164]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band from Baffin Bay to ME ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-70.734375 41.765625 -65.8125 77.90625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[12:1:48][108:1:114]"
             (geog/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-three-dims
  (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][22:1:34][169:1:200]"
           (geog/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][20:1:37][152:1:199]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][23:1:27][156:1:237]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][21:1:65][156:1:164]"
             (geog/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-four-dims
  (let [dims (array-map :Dim3 {:Size 10}
                        :Dim4 {:Size 20}
                        :Latitude {:Size 180}
                        :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][0:1:19][22:1:34][169:1:200]"
           (geog/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][20:1:37][152:1:199]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][23:1:27][156:1:237]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][21:1:65][156:1:164]"
             (geog/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-ordering-preserved
  (let [dims (array-map :Dim3 {:Size 10}
                        :Longitude {:Size 360}
                        :Latitude {:Size 180}
                        :Dim4 {:Size 20})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][169:1:200][22:1:34][0:1:19]"
           (geog/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Longitude {:Size 360}
                          :Latitude {:Size 180})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][152:1:199][20:1:37]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Longitude {:Size 360}
                          :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[156:1:237][0:1:9][0:1:19][23:1:27]"
             (geog/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Longitude {:Size 360}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Dim3 {:Size 10})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (geog/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[156:1:164][0:1:19][21:1:65][0:1:9]"
             (geog/format-opendap-bounds bounding-info))))))

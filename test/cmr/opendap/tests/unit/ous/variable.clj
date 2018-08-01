(ns cmr.opendap.tests.unit.ous.variable
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.concepts.variable :as variable]
    [cmr.opendap.testing.config :as test-system]))

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

(deftest lon-dim
  (is (nil? (variable/lon-dim (variable/extract-dimensions no-spatial-dims))))
  (is (= {:Size 270, :Name :Longitude, :Type nil}
         (variable/lon-dim (variable/extract-dimensions lat-lon-dims))))
  (is (= {:Size 270, :Name :Longitude, :Type nil}
         (variable/lon-dim (variable/extract-dimensions lat-lon-dims-mixed-order))))
  (is (= {:Size 200, :Name :XDim, :Type nil}
         (variable/lon-dim (variable/extract-dimensions x-y-dims))))
  (is (= {:Size 36000, :Name :lon, :Type :LONGITUDE_DIMENSION}
         (variable/lon-dim (variable/extract-dimensions ummvar-1-2-dims)))))

(deftest lat-dim
  (is (nil? (variable/lat-dim (variable/extract-dimensions no-spatial-dims))))
  (is (= {:Size 130, :Name :Latitude, :Type nil}
         (variable/lat-dim (variable/extract-dimensions lat-lon-dims))))
  (is (= {:Size 130, :Name :Latitude, :Type nil}
         (variable/lat-dim (variable/extract-dimensions lat-lon-dims-mixed-order))))
  (is (= {:Size 100, :Name :YDim, :Type nil}
         (variable/lat-dim (variable/extract-dimensions x-y-dims))))
  (is (= {:Size 17999, :Name :lat, :Type :LATITUDE_DIMENSION}
         (variable/lat-dim (variable/extract-dimensions ummvar-1-2-dims)))))

(deftest restructure-dims
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
          :XDim {:Size 200 :Type nil :Name :XDim}
          :YDim {:Size 100 :Type nil :Name :YDim}}
         (variable/restructure-dims
          (get-in x-y-dims [:umm :Dimensions]))))
  (is (= {:LATITUDE_DIMENSION {:Size 17999 :Type :LATITUDE_DIMENSION :Name :lat}
          :LONGITUDE_DIMENSION {:Size 36000 :Type :LONGITUDE_DIMENSION :Name :lon}
          :time {:Size 1 :Type :OTHER :Name :time}}
         (variable/restructure-dims
          (get-in ummvar-1-2-dims [:umm :Dimensions])))))

(deftest extract-dimensions
  (let [dims (variable/extract-dimensions lat-lon-dims)]
    (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
            :Latitude {:Size 130 :Type nil :Name :Latitude}
            :Longitude {:Size 270 :Type nil :Name :Longitude}}
           dims))
    (is (= [:EmisFreqIR :Latitude :Longitude] (keys dims))))
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
          :YDim {:Size 100 :Type nil :Name :YDim}
          :XDim {:Size 200 :Type nil :Name :XDim}}
         (variable/extract-dimensions x-y-dims)))
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}}
         (variable/extract-dimensions no-spatial-dims)))
  (let [dims (variable/extract-dimensions lat-lon-dims-mixed-order)]
    (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
            :Latitude {:Size 130 :Type nil :Name :Latitude}
            :Longitude {:Size 270 :Type nil :Name :Longitude}}
           dims))
    (is (= [:Longitude :EmisFreqIR :Latitude] (keys dims)))))

(deftest normalize-lat-lon
  (is (= {:EmisFreqIR {:Size 4 :Type nil :Name :EmisFreqIR}
          :Latitude {:Size 130 :Type nil :Name :Latitude}
          :Longitude {:Size 270 :Type nil :Name :Longitude}}
         (variable/normalize-lat-lon
          (variable/extract-dimensions lat-lon-dims))))
  (is (= {:EmisFreqIR {:Name :EmisFreqIR :Size 4 :Type nil}
          :Latitude {:Name :YDim :Size 100 :Type nil}
          :Longitude {:Name :XDim :Size 200 :Type nil}}
         (variable/normalize-lat-lon
          (variable/extract-dimensions x-y-dims))))
  (is (= {:Latitude {:Name :lat :Size 17999 :Type :LATITUDE_DIMENSION}
          :Longitude {:Name :lon :Size 36000 :Type :LONGITUDE_DIMENSION}
          :time {:Name :time :Size 1 :Type :OTHER}}
         (variable/normalize-lat-lon
          (variable/extract-dimensions ummvar-1-2-dims))))
  (is (= {:EmisFreqIR {:Name :EmisFreqIR :Size 4 :Type nil}
          :Latitude nil
          :Longitude nil}
         (variable/normalize-lat-lon
          (variable/extract-dimensions no-spatial-dims)))))

(deftest extract-bounds
  (is (= {:low {:lon -180 :lat -90}
          :high {:lon 180 :lat 90}
          :lat-reversed? false}
         (into {} (variable/extract-bounds ummvar-1-2-bounds)))))

(deftest create-opendap-bounds
  (let [dims (array-map :Longitude {:Size 360} :Latitude {:Size 180})
        bounds [-27.421875 53.296875 18.5625 69.75]
        lookup-array (variable/create-opendap-bounds
                      dims bounds {:reversed? true})]
    (is (= 152 (get-in lookup-array [:low :lon])))
    (is (= 20 (get-in lookup-array [:low :lat])))
    (is (= 199 (get-in lookup-array [:high :lon])))
    (is (= 37 (get-in lookup-array [:high :lat])))))

(deftest format-opendap-bounds-no-lat-lon
  (is (= "MyVar"
         (variable/format-opendap-bounds {:name "MyVar"}))))

(deftest format-opendap-bounds-lat-lon-only
  (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds
                                 dims bounds {:reversed? true})}]
    (is (= "MyVar[22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band from Baffin Bay to ME ..."
    (let [dims (array-map :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-70.734375 41.765625 -65.8125 77.90625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[12:1:48][108:1:114]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-three-dims
  (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 {:Size 10} :Latitude {:Size 180} :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-four-dims
  (let [dims (array-map :Dim3 {:Size 10}
                        :Dim4 {:Size 20}
                        :Latitude {:Size 180}
                        :Longitude {:Size 360})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][0:1:19][22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Longitude {:Size 360})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-ordering-preserved
  (let [dims (array-map :Dim3 {:Size 10}
                        :Longitude {:Size 360}
                        :Latitude {:Size 180}
                        :Dim4 {:Size 20})
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
    (is (= "MyVar[0:1:9][169:1:200][22:1:34][0:1:19]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Longitude {:Size 360}
                          :Latitude {:Size 180})
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[0:1:9][0:1:19][152:1:199][20:1:37]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Longitude {:Size 360}
                          :Dim3 {:Size 10}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180})
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[156:1:237][0:1:9][0:1:19][23:1:27]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Longitude {:Size 360}
                          :Dim4 {:Size 20}
                          :Latitude {:Size 180}
                          :Dim3 {:Size 10})
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds
                                   dims bounds {:reversed? true})}]
      (is (= "MyVar[156:1:164][0:1:19][21:1:65][0:1:9]"
             (variable/format-opendap-bounds bounding-info))))))

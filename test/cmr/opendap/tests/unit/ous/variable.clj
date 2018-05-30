(ns cmr.opendap.tests.unit.ous.variable
  "Note: this namespace is exclusively for unit tests."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.ous.variable :as variable]))

(deftest lon-lo-phase-shift
  (is (= 0 (variable/lon-lo-phase-shift 360 -180)))
  (is (= 179 (variable/lon-lo-phase-shift 360 0)))
  (is (= 359 (variable/lon-lo-phase-shift 360 180)))
  (is (= 156 (variable/lon-lo-phase-shift 360 -23.0625))))

(deftest lon-hi-phase-shift
  (is (= 0 (variable/lon-hi-phase-shift 360 -180)))
  (is (= 180 (variable/lon-hi-phase-shift 360 0)))
  (is (= 359 (variable/lon-hi-phase-shift 360 180)))
  (is (= 237 (variable/lon-hi-phase-shift 360 57.09375))))

(deftest lat-lo-phase-shift
  (is (= 179 (variable/lat-lo-phase-shift 180 -90)))
  (is (= 90 (variable/lat-lo-phase-shift 180 0)))
  (is (= 0 (variable/lat-lo-phase-shift 180 90)))
  (is (= 27 (variable/lat-lo-phase-shift 180 63.5625))))

(deftest lat-hi-phase-shift
  (is (= 179 (variable/lat-hi-phase-shift 180 -90)))
  (is (= 89 (variable/lat-hi-phase-shift 180 0)))
  (is (= 0 (variable/lat-hi-phase-shift 180 90)))
  (is (= 23 (variable/lat-hi-phase-shift 180 66.09375))))

(def lat-lon-dims
  [{:Name "EmisFreqIR"
    :Size 4}
   {:Name "Latitude"
    :Size 130}
   {:Name "Longitude"
    :Size 270}])

(def lat-lon-dims-mixed-order
  [{:Name "Longitude"
    :Size 270}
   {:Name "EmisFreqIR"
    :Size 4}
   {:Name "Latitude"
    :Size 130}])

(def x-y-dims
  [{:Name "EmisFreqIR"
    :Size 4}
   {:Name "YDim"
    :Size 100}
   {:Name "XDim"
    :Size 200}])

(def no-spatial-dims
  [{:Name "EmisFreqIR"
    :Size 4}])

(deftest parse-lat-lon
  (testing "With Lat/Lon ..."
    (is (= [270 130] (variable/parse-lat-lon lat-lon-dims))))
  (testing "With XDim/YDim ..."
    (is (= [200 100] (variable/parse-lat-lon x-y-dims))))
  (testing "With none; using defaults ..."
    (is (= [360.0 180.0] (variable/parse-lat-lon no-spatial-dims)))))

(deftest extract-dimensions
  (let [dims (variable/extract-dimensions {:umm {:Dimensions lat-lon-dims}})]
    (is (= (array-map :EmisFreqIR 4 :Latitude 130 :Longitude 270)
           dims))
    (is (= [:EmisFreqIR :Latitude :Longitude] (keys dims))))
  (is (= (array-map :EmisFreqIR 4 :YDim 100 :XDim 200)
         (variable/extract-dimensions {:umm {:Dimensions x-y-dims}})))
  (is (= (array-map :EmisFreqIR 4)
         (variable/extract-dimensions {:umm {:Dimensions no-spatial-dims}})))
  (let [dims (variable/extract-dimensions
              {:umm {:Dimensions lat-lon-dims-mixed-order}})]
    (is (= (array-map :Longitude 270 :EmisFreqIR 4 :Latitude 130)
           dims))
    (is (= [:Longitude :EmisFreqIR :Latitude] (keys dims)))))

(deftest create-opendap-bounds
  (let [dims (array-map :Longitude 360 :Latitude 180)
        bounds [-27.421875 53.296875 18.5625 69.75]
        lookup-array (variable/create-opendap-bounds dims bounds)]
    (is (= 152 (get-in lookup-array [:low :lon])))
    (is (= 20 (get-in lookup-array [:low :lat])))
    (is (= 199 (get-in lookup-array [:high :lon])))
    (is (= 37 (get-in lookup-array [:high :lat])))))

(deftest format-opendap-bounds-no-lat-lon
  (is (= "MyVar"
         (variable/format-opendap-bounds {:name "MyVar"}))))

(deftest format-opendap-bounds-lat-lon-only
  (let [dims (array-map :Latitude 180 :Longitude 360)
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds dims bounds)}]
    (is (= "MyVar[22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Latitude 180 :Longitude 360)
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Latitude 180 :Longitude 360)
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Latitude 180 :Longitude 360)
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-three-dims
  (let [dims (array-map :Dim3 10 :Latitude 180 :Longitude 360)
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds dims bounds)}]
    (is (= "MyVar[0:1:9][22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 10 :Latitude 180 :Longitude 360)
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 10 :Latitude 180 :Longitude 360)
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 10 :Latitude 180 :Longitude 360)
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-four-dims
  (let [dims (array-map :Dim3 10 :Dim4 20 :Latitude 180 :Longitude 360)
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds dims bounds)}]
    (is (= "MyVar[0:1:9][0:1:19][22:1:34][169:1:200]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 10 :Dim4 20 :Latitude 180 :Longitude 360)
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][0:1:19][20:1:37][152:1:199]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Dim3 10 :Dim4 20 :Latitude 180 :Longitude 360)
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][0:1:19][23:1:27][156:1:237]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Dim3 10 :Dim4 20 :Latitude 180 :Longitude 360)
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][0:1:19][21:1:65][156:1:164]"
             (variable/format-opendap-bounds bounding-info))))))

(deftest format-opendap-bounds-ordering-preserved
  (let [dims (array-map :Dim3 10 :Longitude 360 :Latitude 180 :Dim4 20)
        bounds [-9.984375 56.109375 19.828125 67.640625]
        bounding-info {:name "MyVar"
                       :bounds bounds
                       :dimensions dims
                       :opendap (variable/create-opendap-bounds dims bounds)}]
    (is (= "MyVar[0:1:9][169:1:200][22:1:34][0:1:19]"
           (variable/format-opendap-bounds bounding-info))))
  (testing "Bound around Iceland, GB, and Scandanavia ..."
    (let [dims (array-map :Dim3 10 :Dim4 20 :Longitude 360 :Latitude 180)
          bounds [-27.421875 53.296875 18.5625 69.75]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[0:1:9][0:1:19][152:1:199][20:1:37]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching to Scandanavia ..."
    (let [dims (array-map :Longitude 360 :Dim3 10 :Dim4 20 :Latitude 180)
          bounds [-23.0625 63.5625 57.09375 66.09375]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[156:1:237][0:1:9][0:1:19][23:1:27]"
             (variable/format-opendap-bounds bounding-info)))))
  (testing "Narrow band around Icelend stretching down to Africa ..."
    (let [dims (array-map :Longitude 360 :Dim4 20 :Latitude 180 :Dim3 10)
          bounds [-23.34375 25.59375 -16.03125 68.625]
          bounding-info {:name "MyVar"
                         :bounds bounds
                         :dimensions dims
                         :opendap (variable/create-opendap-bounds dims bounds)}]
      (is (= "MyVar[156:1:164][0:1:19][21:1:65][0:1:9]"
             (variable/format-opendap-bounds bounding-info))))))

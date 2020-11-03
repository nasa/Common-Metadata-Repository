(ns cmr.indexer.test.data.concepts.collection.resolution
  "This namespace conducts unit tests on the resolution namespace."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as common-util :refer [are3]]
    [cmr.indexer.data.concepts.collection.resolution :as res]))

(deftest get-non-range-data-resolution-test
  "Test the extraction of non range data resolutions."

  (are3 [expected test]
    (is (= expected
           (res/get-non-range-data-resolutions test)))

    "testing Non gridded, gridded, and generic type extractions"
    '(5 6 7 8)
    [{:XDimension 5
      :YDimension 6
      :Unit "Meters"
      :ViewingAngleType "At Nadir"
      :ScanDirection "Cross Track"}
     {:XDimension 7
      :YDimension 8
      :Unit "Meters"}
     {:XDimension 7
      :Unit "Meters"}]

    "Testing non gridded with the viewing angle that should be removed."
    '(7 8)
    [{:XDimension 5
      :YDimension 6
      :Unit "Meters"
      :ViewingAngleType "Scan Extremes"
      :ScanDirection "Cross Track"}
     {:XDimension 7
      :YDimension 8
      :Unit "Meters"}
     {:XDimension 7
      :Unit "Meters"}]))

(deftest get-range-data-resolution-test
  "Test the extraction of range data resolutions."

  (are3 [expected test]
    (is (= expected
           (res/get-range-data-resolutions test)))

    "testing Non gridded and gridded range type extractions"
    '(5 9 6 10 7 8 7.0)
    [{:MinimumXDimension 5
      :MinimumYDimension 6
      :MaximumXDimension 9
      :MaximumYDimension 10
      :Unit "Meters"
      :ViewingAngleType "At Nadir"
      :ScanDirection "Cross Track"}
     {:MinimumXDimension 7
      :MaximumXDimension 8
      :Unit "Meters"}
     {:MinimumXDimension 0.007
      :MaximumYDimension 0.007
      :Unit "Kilometers"}]

    "Testing non gridded rage with the viewing angle that should be removed."
    '(7 8 7.0)
    [{:MinimumXDimension 5
      :MinimumYDimension 6
      :MaximumXDimension 9
      :MaximumYDimension 10
      :Unit "Meters"
      :ViewingAngleType "Scan Extremes"
      :ScanDirection "Cross Track"}
     {:MinimumXDimension 7
      :MaximumXDimension 8
      :Unit "Meters"}
     {:MinimumXDimension 0.007
      :MaximumYDimension 0.007
      :Unit "Kilometers"}]))

(deftest get-horizontal-data-resolutions-test
  "Test the extraction and conversion of horizontal data resolutions."

  (are3 [expected test]
    (is (= expected
           (res/get-horizontal-data-resolutions test)))

    "Testing the full extraction and conversion."
    '(0 5 6 7.0 8.0 9 10 22224.012 24076.013 11.265408 12.874752 15 17 16 18 37040.02)
    {:VariesResolution "Varies"
     :PointResolution "Point"
     :NonGriddedResolutions [{:XDimension 5
                              :YDimension 6
                              :Unit "Meters"
                              :ViewingAngleType "At Nadir"
                              :ScanDirection "Cross Track"}
                             {:XDimension 0.007
                              :YDimension 0.008
                              :Unit "Kilometers"
                              :ViewingAngleType "At Nadir"
                              :ScanDirection "Cross Track"}]
     :NonGriddedRangeResolutions [{:MinimumXDimension 5
                                   :MinimumYDimension 6
                                   :MaximumXDimension 9
                                   :MaximumYDimension 10
                                   :Unit "Meters"
                                   :ViewingAngleType "At Nadir"
                                   :ScanDirection "Cross Track"}]
     :GriddedResolutions [{:XDimension 12
                           :YDimension 13
                           :Unit "Nautical Miles"}
                          {:XDimension 0.007
                           :YDimension 0.008
                           :Unit "Statute Miles"}]
     :GriddedRangeResolutions [{:MinimumXDimension 15
                                :MinimumYDimension 16
                                :MaximumXDimension 17
                                :MaximumYDimension 18
                                :Unit "Meters"}]
     :GenericResolutions [{:XDimension 20
                           :YDimension 20
                           :Unit "Nautical Miles"}
                          {:XDimension 0.007
                           :YDimension 0.008
                           :Unit "Statute Miles"}]}))

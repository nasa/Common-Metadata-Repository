(ns cmr.system-int-test.search.granule-platform-instrument-sensor-search-test
  "Integration test for CMR granule search by platform, instrument and sensor short-names"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-by-platform-short-names
  (let [p0 (dc/platform "platform_Inherit")
        p01 (dc/platform "platform_ONE")
        p1 (dc/platform "platform_Sn A")
        p2 (dc/platform "platform_Sn B")
        p3 (dc/platform "platform_SnA")
        p4 (dc/platform "platform_Snx")
        p5 (dc/platform "platform_x")
        p6 (dc/platform "PLATform_X")
        pr1 (dg/platform-ref "platform_Sn A")
        pr2 (dg/platform-ref "platform_Sn B")
        pr3 (dg/platform-ref "platform_SnA")
        pr4 (dg/platform-ref "platform_Snx")
        pr5 (dg/platform-ref "platform_ONE")
        pr6 (dg/platform-ref "platform_x")
        pr7 (dg/platform-ref "PLATform_X")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2 p3 p4]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0 p01]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr5]}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr6]}))
        gran9 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr7]}))]

    (index/refresh-elastic-index)

    (testing "search by platform, single value"
      (are [platform-sn items] (d/refs-match? items (search/find-refs :granule {:platform platform-sn}))
           "platform_Sn A" [gran1 gran2]
           "platform_Sn B" [gran2 gran3]
           "platform_x" [gran8 gran9]
           "BLAH" []))
    (testing "search by platform, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4]
                         (search/find-refs :granule {"platform[]" ["platform_SnA" "platform_Sn A"]}))))
    (testing "search by platform, inheritance"
      (is (d/refs-match? [gran6]
                         (search/find-refs :granule {"platform[]" ["platform_Inherit"]}))))
    (testing "search by platform, ignore case true"
      (is (d/refs-match? [gran8 gran9]
                         (search/find-refs :granule {"platform[]" ["platform_x"]
                                                        "options[platform][ignore-case]" "true"}))))
    (testing "search by platform, ignore case false"
      (is (d/refs-match? [gran8]
                         (search/find-refs :granule {"platform[]" ["platform_x"]
                                                        "options[platform][ignore-case]" "false"}))))
    (testing "search by platform, wildcard *"
      (is (d/refs-match? [gran1 gran2 gran3]
                         (search/find-refs :granule {"platform[]" ["platform_Sn *"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platform, wildcard ?"
      (is (d/refs-match? [gran4 gran5]
                         (search/find-refs :granule {"platform[]" ["platform_Sn?"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platform, options :or."
      (is (d/refs-match? [gran1 gran2 gran3]
                         (search/find-refs :granule {"platform[]" ["platform_Sn B" "platform_Sn A"]
                                                        "options[platform][or]" "true"}))))
    (testing "search by platform, options :and."
      (is (d/refs-match? [gran2]
                         (search/find-refs :granule {"platform[]" ["platform_Sn B" "platform_Sn A"]
                                                        "options[platform][and]" "true"}))))))

(deftest search-by-instrument-short-names
  (let [i0 (dc/instrument "instrument_Inherit")
        i01 (dc/instrument "instrument_ONE")
        p0 (dc/platform "collection_platform" i0 i01)
        i1 (dg/instrument-ref "instrument_Sn A")
        i2 (dg/instrument-ref "instrument_Sn b")
        i3 (dg/instrument-ref "instrument_SnA")
        i4 (dg/instrument-ref "instrument_Snx")
        i5 (dg/instrument-ref "instrument_ONE")
        i6 (dg/instrument-ref "instrument_x")
        i7 (dg/instrument-ref "InstruMENT_X")
        pr1 (dg/platform-ref "platform_1" i1)
        pr2 (dg/platform-ref "platform_2" i2)
        pr3 (dg/platform-ref "platform_3" i3)
        pr4 (dg/platform-ref "platform_4" i4)
        pr5 (dg/platform-ref "platform_5" i1 i2)
        pr6 (dg/platform-ref "platform_6" i5)
        pr7 (dg/platform-ref "platform_7" i6)
        pr8 (dg/platform-ref "platform_8" i7)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr5]}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr6]}))
        gran9 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr7]}))
        gran10 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [pr8]}))]

    (index/refresh-elastic-index)

    (testing "search by instrument, single value"
      (are [instrument-sn items] (d/refs-match? items (search/find-refs :granule {:instrument instrument-sn}))
           "instrument_Sn A" [gran1 gran2 gran6]
           "instrument_SnA" [gran4]
           "instrument_x" [gran9 gran10]
           "BLAH" []))
    (testing "search by instrument, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_SnA" "instrument_Sn A"]}))))
    (testing "search by instrument, inheritance"
      (is (d/refs-match? [gran7]
                         (search/find-refs :granule {"instrument[]" ["instrument_Inherit"]}))))
    (testing "search by instrument, ignore case true"
      (is (d/refs-match? [gran9 gran10]
                         (search/find-refs :granule {"instrument[]" ["instrument_x"]
                                                        "options[instrument][ignore-case]" "true"}))))
    (testing "search by instrument, ignore case false"
      (is (d/refs-match? [gran9]
                         (search/find-refs :granule {"instrument[]" ["instrument_x"]
                                                        "options[instrument][ignore-case]" "false"}))))
    (testing "search by instrument, wildcard *"
      (is (d/refs-match? [gran1 gran2 gran3 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn *"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by instrument, wildcard ?"
      (is (d/refs-match? [gran4 gran5]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn?"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by instrument, options :or."
      (is (d/refs-match? [gran1 gran2 gran3 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn b" "instrument_Sn A"]
                                                        "options[instrument][or]" "true"}))))
    (testing "search by instrument, options :and."
      (is (d/refs-match? [gran2 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn b" "instrument_Sn A"]
                                                        "options[instrument][and]" "true"}))))))

(deftest search-by-sensor-short-names
  (let [s0 (dc/sensor "sensor_Inherit")
        s01 (dc/sensor "sensor_ONE")
        i0 (dc/instrument "collection_instrument" s0 s01)
        p0 (dc/platform "collection_platform" i0)
        s1 (dg/sensor-ref "sensor_Sn A")
        s2 (dg/sensor-ref "sensor_Sn b")
        s3 (dg/sensor-ref "sensor_SnA")
        s4 (dg/sensor-ref "sensor_Snx")
        s5 (dg/sensor-ref "sensor_ONE")
        s6 (dg/sensor-ref "sensor_x")
        s7 (dg/sensor-ref "SEnsor_X")
        i1 (dg/instrument-ref "instrument_1" s1)
        i2 (dg/instrument-ref "instrument_2" s2)
        i3 (dg/instrument-ref "instrument_3" s3)
        i4 (dg/instrument-ref "instrument_4" s4)
        i5 (dg/instrument-ref "instrument_5" s1 s2)
        i6 (dg/instrument-ref "instrument_6" s5)
        i7 (dg/instrument-ref "instrument_7" s6)
        i8 (dg/instrument-ref "instrument_8" s7)
        p1 (dg/platform-ref "platform_1" i1)
        p2 (dg/platform-ref "platform_2" i2)
        p3 (dg/platform-ref "platform_3" i3)
        p4 (dg/platform-ref "platform_4" i4)
        p5 (dg/platform-ref "platform_5" i5)
        p6 (dg/platform-ref "platform_6" i1 i2)
        p7 (dg/platform-ref "platform_7" i6)
        p8 (dg/platform-ref "platform_8" i7)
        p9 (dg/platform-ref "platform_9" i8)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p1 p2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p5]}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p6]}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))
        gran9 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [p7]}))
        gran10 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [p8]}))
        gran11 (d/ingest "CMR_PROV1" (dg/granule coll2 {:platform-refs [p9]}))]

    (index/refresh-elastic-index)

    (testing "search by sensor, single value"
      (are [sensor-sn items] (d/refs-match? items (search/find-refs :granule {:sensor sensor-sn}))
           "sensor_Sn A" [gran1 gran2 gran6 gran7]
           "sensor_SnA" [gran4]
           "sensor_x" [gran10 gran11]
           "BLAH" []))
    (testing "search by sensor, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_SnA" "sensor_Sn A"]}))))
    (testing "search by sensor, inheritance"
      (is (d/refs-match? [gran8]
                         (search/find-refs :granule {"sensor[]" ["sensor_Inherit"]}))))
    (testing "search by sensor, ignore case true"
      (is (d/refs-match? [gran10 gran11]
                         (search/find-refs :granule {"sensor[]" ["sensor_x"]
                                                        "options[sensor][ignore-case]" "true"}))))
    (testing "search by sensor, ignore case false"
      (is (d/refs-match? [gran10]
                         (search/find-refs :granule {"sensor[]" ["sensor_x"]
                                                        "options[sensor][ignore-case]" "false"}))))
    (testing "search by sensor, wildcard *"
      (is (d/refs-match? [gran1 gran2 gran3 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn *"]
                                                        "options[sensor][pattern]" "true"}))))
    (testing "search by sensor, wildcard ?"
      (is (d/refs-match? [gran4 gran5]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn?"]
                                                        "options[sensor][pattern]" "true"}))))
    (testing "search by sensor, options :or."
      (is (d/refs-match? [gran1 gran2 gran3 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn b" "sensor_Sn A"]
                                                        "options[sensor][or]" "true"}))))
    (testing "search by sensor, options :and."
      (is (d/refs-match? [gran2 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn b" "sensor_Sn A"]
                                                        "options[sensor][and]" "true"}))))))

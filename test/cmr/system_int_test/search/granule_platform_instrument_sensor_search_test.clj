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
        p1 (dc/platform "platform_Sn A")
        p2 (dc/platform "platform_Sn a")
        p3 (dc/platform "platform_SnA")
        p4 (dc/platform "platform_Snx")
        pr1 (dg/platform-ref "platform_Sn A")
        pr2 (dg/platform-ref "platform_Sn a")
        pr3 (dg/platform-ref "platform_SnA")
        pr4 (dg/platform-ref "platform_Snx")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2 p3 p4]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))]

    (index/flush-elastic-index)

    (testing "search by platform, single value"
      (are [platform-sn items] (d/refs-match? items (search/find-refs :granule {:platform platform-sn}))
           "platform_Sn A" [gran1 gran2]
           "platform_Sn a" [gran2 gran3]
           "BLAH" []))
    (testing "search by platform, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4]
                         (search/find-refs :granule {"platform[]" ["platform_SnA" "platform_Sn A"]}))))
    (testing "search by platform, inheritance"
      (is (d/refs-match? [gran6]
                         (search/find-refs :granule {"platform[]" ["platform_Inherit"]}))))
    (testing "search by platform, ignore case true"
      (is (d/refs-match? [gran1 gran2 gran3]
                         (search/find-refs :granule {"platform[]" ["platform_Sn A"]
                                                        "options[platform][ignore-case]" "true"}))))
    (testing "search by platform, ignore case false"
      (is (d/refs-match? [gran1 gran2]
                         (search/find-refs :granule {"platform[]" ["platform_Sn A"]
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
                         (search/find-refs :granule {"platform[]" ["platform_Sn a" "platform_Sn A"]
                                                        "options[platform][or]" "true"}))))
    (testing "search by platform, options :and."
      (is (d/refs-match? [gran2]
                         (search/find-refs :granule {"platform[]" ["platform_Sn a" "platform_Sn A"]
                                                        "options[platform][and]" "true"}))))))

(deftest search-by-instrument-short-names
  (let [i0 (dc/instrument "instrument_Inherit")
        p0 (dc/platform "collection_platform" i0)
        i1 (dg/instrument-ref "instrument_Sn A")
        i2 (dg/instrument-ref "instrument_Sn a")
        i3 (dg/instrument-ref "instrument_SnA")
        i4 (dg/instrument-ref "instrument_Snx")
        pr1 (dg/platform-ref "platform_1" i1)
        pr2 (dg/platform-ref "platform_2" i2)
        pr3 (dg/platform-ref "platform_3" i3)
        pr4 (dg/platform-ref "platform_4" i4)
        pr5 (dg/platform-ref "platform_5" i1 i2)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [pr5]}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))]

    (index/flush-elastic-index)

    (testing "search by instrument, single value"
      (are [instrument-sn items] (d/refs-match? items (search/find-refs :granule {:instrument instrument-sn}))
           "instrument_Sn A" [gran1 gran2 gran6]
           "instrument_SnA" [gran4]
           "BLAH" []))
    (testing "search by instrument, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_SnA" "instrument_Sn A"]}))))
    (testing "search by instrument, inheritance"
      (is (d/refs-match? [gran7]
                         (search/find-refs :granule {"instrument[]" ["instrument_Inherit"]}))))
    (testing "search by instrument, ignore case true"
      (is (d/refs-match? [gran1 gran2 gran3 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn A"]
                                                        "options[instrument][ignore-case]" "true"}))))
    (testing "search by instrument, ignore case false"
      (is (d/refs-match? [gran1 gran2 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn A"]
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
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn a" "instrument_Sn A"]
                                                        "options[instrument][or]" "true"}))))
    (testing "search by instrument, options :and."
      (is (d/refs-match? [gran2 gran6]
                         (search/find-refs :granule {"instrument[]" ["instrument_Sn a" "instrument_Sn A"]
                                                        "options[instrument][and]" "true"}))))))

(deftest search-by-sensor-short-names
  (let [s0 (dc/sensor "sensor_Inherit")
        i0 (dc/instrument "collection_instrument" s0)
        p0 (dc/platform "collection_platform" i0)
        s1 (dg/sensor-ref "sensor_Sn A")
        s2 (dg/sensor-ref "sensor_Sn a")
        s3 (dg/sensor-ref "sensor_SnA")
        s4 (dg/sensor-ref "sensor_Snx")
        i1 (dg/instrument-ref "instrument_1" s1)
        i2 (dg/instrument-ref "instrument_2" s2)
        i3 (dg/instrument-ref "instrument_3" s3)
        i4 (dg/instrument-ref "instrument_4" s4)
        i5 (dg/instrument-ref "instrument_5" s1 s2)
        p1 (dg/platform-ref "platform_1" i1)
        p2 (dg/platform-ref "platform_2" i2)
        p3 (dg/platform-ref "platform_3" i3)
        p4 (dg/platform-ref "platform_4" i4)
        p5 (dg/platform-ref "platform_5" i5)
        p6 (dg/platform-ref "platform_6" i1 i2)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p0]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p1 p2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p2]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p4]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p5]}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll1 {:platform-refs [p6]}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll2 {}))]

    (index/flush-elastic-index)

    (testing "search by sensor, single value"
      (are [sensor-sn items] (d/refs-match? items (search/find-refs :granule {:sensor sensor-sn}))
           "sensor_Sn A" [gran1 gran2 gran6 gran7]
           "BLAH" []))
    (testing "search by sensor, multiple values"
      (is (d/refs-match? [gran1 gran2 gran4 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_SnA" "sensor_Sn A"]}))))
    (testing "search by sensor, inheritance"
      (is (d/refs-match? [gran8]
                         (search/find-refs :granule {"sensor[]" ["sensor_Inherit"]}))))
    (testing "search by sensor, ignore case true"
      (is (d/refs-match? [gran1 gran2 gran3 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn A"]
                                                        "options[sensor][ignore-case]" "true"}))))
    (testing "search by sensor, ignore case false"
      (is (d/refs-match? [gran1 gran2 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn A"]
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
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn a" "sensor_Sn A"]
                                                        "options[sensor][or]" "true"}))))
    (testing "search by sensor, options :and."
      (is (d/refs-match? [gran2 gran6 gran7]
                         (search/find-refs :granule {"sensor[]" ["sensor_Sn a" "sensor_Sn A"]
                                                        "options[sensor][and]" "true"}))))))

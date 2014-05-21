(ns cmr.system-int-test.search.collection-platform-instrument-sensor-search-test
  "Integration test for CMR collection search by platform, instrument and sensor short-names"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-platform-short-names
  (let [p1 (dc/platform "platform_Sn A")
        p2 (dc/platform "platform_Sn B")
        p3 (dc/platform "platform_SnA")
        p4 (dc/platform "platform_Snx")
        p5 (dc/platform "PLATFORM_X")
        p6 (dc/platform "platform_x")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search by platform, single value"
      (are [platform-sn items] (d/refs-match? items (search/find-refs :collection {:platform platform-sn}))
           "platform_Sn A" [coll1 coll2]
           "platform_x" [coll6 coll7]
           "BLAH" []))
    (testing "search by platform, multiple values"
      (is (d/refs-match? [coll1 coll2 coll4]
                         (search/find-refs :collection {"platform[]" ["platform_SnA" "platform_Sn A"]}))))
    (testing "search by platform, ignore case true"
      (is (d/refs-match? [coll6 coll7]
                         (search/find-refs :collection {"platform[]" ["platform_x"]
                                                        "options[platform][ignore-case]" "true"}))))
    (testing "search by platform, ignore case false"
      (is (d/refs-match? [coll7]
                         (search/find-refs :collection {"platform[]" ["platform_x"]
                                                        "options[platform][ignore-case]" "false"}))))
    (testing "search by platform, wildcard *"
      (is (d/refs-match? [coll1 coll2 coll3]
                         (search/find-refs :collection {"platform[]" ["platform_Sn *"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platform, wildcard ?"
      (is (d/refs-match? [coll4 coll5]
                         (search/find-refs :collection {"platform[]" ["platform_Sn?"]
                                                        "options[platform][pattern]" "true"}))))
    (testing "search by platform, options :or."
      (is (d/refs-match? [coll1 coll2 coll3]
                         (search/find-refs :collection {"platform[]" ["platform_Sn B" "platform_Sn A"]
                                                        "options[platform][or]" "true"}))))
    (testing "search by platform, options :and."
      (is (d/refs-match? [coll2]
                         (search/find-refs :collection {"platform[]" ["platform_Sn B" "platform_Sn A"]
                                                        "options[platform][and]" "true"}))))))

(deftest search-by-instrument-short-names
  (let [i1 (dc/instrument "instrument_Sn A")
        i2 (dc/instrument "instrument_Sn B")
        i3 (dc/instrument "instrument_SnA")
        i4 (dc/instrument "instrument_Snx")
        i5 (dc/instrument "InstruMENT_X")
        i6 (dc/instrument "instrument_x")
        p1 (dc/platform "platform_1" i1)
        p2 (dc/platform "platform_2" i2)
        p3 (dc/platform "platform_3" i3)
        p4 (dc/platform "platform_4" i4)
        p5 (dc/platform "platform_5" i1 i2)
        p6 (dc/platform "platform_6" i5)
        p7 (dc/platform "platform_7" i6)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p7]}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search by instrument, single value"
      (are [instrument-sn items] (d/refs-match? items (search/find-refs :collection {:instrument instrument-sn}))
           "instrument_Sn A" [coll1 coll2 coll6]
           "instrument_x" [coll7 coll8]
           "BLAH" []))
    (testing "search by instrument, multiple values"
      (is (d/refs-match? [coll1 coll2 coll4 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_SnA" "instrument_Sn A"]}))))
    (testing "search by instrument, ignore case true"
      (is (d/refs-match? [coll7 coll8]
                         (search/find-refs :collection {"instrument[]" ["instrument_x"]
                                                        "options[instrument][ignore-case]" "true"}))))
    (testing "search by instrument, ignore case false"
      (is (d/refs-match? [coll8]
                         (search/find-refs :collection {"instrument[]" ["instrument_x"]
                                                        "options[instrument][ignore-case]" "false"}))))
    (testing "search by instrument, wildcard *"
      (is (d/refs-match? [coll1 coll2 coll3 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn *"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by instrument, wildcard ?"
      (is (d/refs-match? [coll4 coll5]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn?"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by instrument, options :or."
      (is (d/refs-match? [coll1 coll2 coll3 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn B" "instrument_Sn A"]
                                                        "options[instrument][or]" "true"}))))
    (testing "search by instrument, options :and."
      (is (d/refs-match? [coll2 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn B" "instrument_Sn A"]
                                                        "options[instrument][and]" "true"}))))))

(deftest search-by-sensor-short-names
  (let [s1 (dc/sensor "sensor_Sn A")
        s2 (dc/sensor "sensor_Sn B")
        s3 (dc/sensor "sensor_SnA")
        s4 (dc/sensor "sensor_Snx")
        s5 (dc/sensor "sensor_x")
        s6 (dc/sensor "SenSOR_X")
        i1 (dc/instrument "instrument_1" s1)
        i2 (dc/instrument "instrument_2" s2)
        i3 (dc/instrument "instrument_3" s3)
        i4 (dc/instrument "instrument_4" s4)
        i5 (dc/instrument "instrument_5" s1 s2)
        i6 (dc/instrument "instrument_6" s5)
        i7 (dc/instrument "instrument_7" s6)
        p1 (dc/platform "platform_1" i1)
        p2 (dc/platform "platform_2" i2)
        p3 (dc/platform "platform_3" i3)
        p4 (dc/platform "platform_4" i4)
        p5 (dc/platform "platform_5" i5)
        p6 (dc/platform "platform_6" i1 i2)
        p7 (dc/platform "platform_7" i6)
        p8 (dc/platform "platform_8" i7)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p7]}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p8]}))
        coll10 (d/ingest "CMR_PROV2" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search by sensor, single value"
      (are [sensor-sn items] (d/refs-match? items (search/find-refs :collection {:sensor sensor-sn}))
           "sensor_Sn A" [coll1 coll2 coll6 coll7]
           "sensor_x" [coll8 coll9]
           "BLAH" []))
    (testing "search by sensor, multiple values"
      (is (d/refs-match? [coll1 coll2 coll4 coll6 coll7]
                         (search/find-refs :collection {"sensor[]" ["sensor_SnA" "sensor_Sn A"]}))))
    (testing "search by sensor, ignore case true"
      (is (d/refs-match? [coll8 coll9]
                         (search/find-refs :collection {"sensor[]" ["sensor_x"]
                                                        "options[sensor][ignore-case]" "true"}))))
    (testing "search by sensor, ignore case false"
      (is (d/refs-match? [coll8]
                         (search/find-refs :collection {"sensor[]" ["sensor_x"]
                                                        "options[sensor][ignore-case]" "false"}))))
    (testing "search by sensor, wildcard *"
      (is (d/refs-match? [coll1 coll2 coll3 coll6 coll7]
                         (search/find-refs :collection {"sensor[]" ["sensor_Sn *"]
                                                        "options[sensor][pattern]" "true"}))))
    (testing "search by sensor, wildcard ?"
      (is (d/refs-match? [coll4 coll5]
                         (search/find-refs :collection {"sensor[]" ["sensor_Sn?"]
                                                        "options[sensor][pattern]" "true"}))))
    (testing "search by sensor, options :or."
      (is (d/refs-match? [coll1 coll2 coll3 coll6 coll7]
                         (search/find-refs :collection {"sensor[]" ["sensor_Sn B" "sensor_Sn A"]
                                                        "options[sensor][or]" "true"}))))
    (testing "search by sensor, options :and."
      (is (d/refs-match? [coll2 coll6 coll7]
                         (search/find-refs :collection {"sensor[]" ["sensor_Sn B" "sensor_Sn A"]
                                                        "options[sensor][and]" "true"}))))))


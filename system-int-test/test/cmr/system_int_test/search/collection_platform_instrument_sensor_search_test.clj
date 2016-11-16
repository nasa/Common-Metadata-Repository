(ns cmr.system-int-test.search.collection-platform-instrument-sensor-search-test
  "Integration test for CMR collection search by platform, instrument and sensor short-names"
  (:require 
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-platform
  (let [p1 (dc/platform {:short-name "platform_Sn A"})
        p2 (dc/platform {:short-name "platform_Sn B"})
        p3 (dc/platform {:short-name "platform_SnA"})
        p4 (dc/platform {:short-name "platform_Snx"})
        p5 (dc/platform {:short-name "PLATFORM_X"})
        p6 (dc/platform {:short-name "platform_x"})

        ;; Platforms to verify the ability to search by KMS platform subfields
        p7 (dc/platform {:short-name "DMSP 5B/F3"})
        p8 (dc/platform {:short-name "diaDEM-1d"})

        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1 p7]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p1 p2 p8]}))
        coll3 (d/ingest "PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "PROV2" (dc/collection {}))
        ;; Added to test SMAP ISO platform and instrument support - note that this collection is
        ;; found in KMS with a category of "Earth Observation Satellites"
        coll9 (d/ingest-concept-with-metadata-file "data/iso_smap/sample_smap_iso_collection.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :format-key :iso-smap})]

    (index/wait-until-indexed)

    (testing "search collections by platform-h"
      (are [items platform-sn options]
           (let [params (merge {:platform-h platform-sn}
                               (when options
                                 {"options[platform-h]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [coll1 coll2] "platform_Sn A" {}
           [coll6 coll7] "platform_x" {}
           [] "BLAH" {}
           [coll9] "SMAP" {}
           [coll1 coll2 coll4] ["platform_SnA" "platform_Sn A"] {}
           [coll6 coll7] ["platform_x"] {:ignore-case true}
           [coll7] ["platform_x"] {:ignore-case false}
           [coll1 coll2 coll3] ["platform_Sn *"] {:pattern true}
           [coll4 coll5] ["platform_Sn?"] {:pattern true}
           [coll2] ["platform_Sn B" "platform_Sn A"] {:and true}))

    (testing "search collections by platform"
      (are [items platform-sn options]
           (let [params (merge {:platform platform-sn}
                               (when options
                                 {"options[platform]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [coll1 coll2] "platform_Sn A" {}
           [coll6 coll7] "platform_x" {}
           [] "BLAH" {}
           [coll9] "SMAP" {}
           [coll1 coll2 coll4] ["platform_SnA" "platform_Sn A"] {}
           [coll6 coll7] ["platform_x"] {:ignore-case true}
           [coll7] ["platform_x"] {:ignore-case false}
           [coll1 coll2 coll3] ["platform_Sn *"] {:pattern true}
           [coll4 coll5] ["platform_Sn?"] {:pattern true}
           [coll2] ["platform_Sn B" "platform_Sn A"] {:and true}))

    (testing "search collections by platform with aql"
      (are [items platform-sn options]
           (let [condition (merge {:sourceName platform-sn} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1 coll2] "platform_Sn A" {}
           [coll1 coll2] "'platform_Sn A'" {}
           [coll7] "platform_x" {}
           [] "BLAH" {}
           [coll1 coll2 coll4] ["platform_SnA" "platform_Sn A"] {}
           [coll6 coll7] ["platform_x"] {:ignore-case true}
           [coll7] ["platform_x"] {:ignore-case false}
           [coll1 coll2 coll3] "platform_Sn %" {:pattern true}
           [coll4 coll5] "platform_Sn_" {:pattern true}
           [coll2] ["platform_Sn B" "platform_Sn A"] {:and true}))

    (testing "Search collections by platform using JSON query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll1 coll2] {:platform {:short_name "platform_Sn A"}}
           [coll6 coll7] {:platform {:short_name "platform_x"}}
           [] {:platform {:short_name "BLAH"}}
           [coll9] {:platform {:short_name "SMAP"}}
           [coll1 coll2 coll4] {:or [{:platform {:short_name "platform_SnA"}}
                                     {:platform {:short_name "platform_Sn A"}}]}
           [coll2] {:and [{:platform {:short_name "platform_Sn B"}}
                          {:platform {:short_name "platform_Sn A"}}]}
           [coll6 coll7] {:platform {:short_name "platform_x" :ignore_case true}}
           [coll7] {:platform {:short_name "platform_x" :ignore_case false}}
           [coll1 coll2 coll3] {:platform {:short_name "platform_Sn *" :pattern true}}
           [coll4 coll5] {:platform {:short_name "platform_Sn?" :pattern true}}

           ;; Test searching on KMS subfields
           [coll1 coll2 coll9] {:platform {:category "Earth Observation Satellites"
                                           :ignore_case false}}
           [] {:platform {:category "EARTH OBSERVATION SATELLITES" :ignore_case false}}
           [coll1 coll2 coll9] {:platform {:category "EARTH OBSERVATION SATELLITES"
                                           :ignore_case true}}
           [coll1] {:platform {:series_entity "DMSP (Defense Meteorological Satellite Program)"}}
           ;; Short name uses KMS case rather than metadata case
           [] {:platform {:short_name "diaDEM-1d" :ignore_case false}}
           [coll2] {:platform {:short_name "diaDEM-1d" :ignore_case true}}
           [coll2] {:platform {:short_name "DIADEM-1D" :ignore_case false}}
           [coll1] {:platform {:long_name "defense METEOR*cal S?tellite *" :pattern true}}
           [] {:platform {:long_name "defense METEOR*cal S?tellite *"}}
           [coll1] {:platform {:uuid "7ed12e98-95b1-406c-a58a-f4bbfa405269"}}
           [coll1] {:platform {:any "7ed12e98*" :pattern true}}))))

(deftest search-by-instrument
  (let [i1 (dc/instrument {:short-name "instrument_Sn A"})
        i2 (dc/instrument {:short-name "instrument_Sn B"})
        i3 (dc/instrument {:short-name "instrument_SnA"})
        i4 (dc/instrument {:short-name "instrument_Snx"})
        i5 (dc/instrument {:short-name "InstruMENT_X"})
        i6 (dc/instrument {:short-name "instrument_x"})

        ;; Instruments to verify the ability to search by KMS instrument subfields
        i7 (dc/instrument {:short-name "atm"})
        i8 (dc/instrument {:short-name "LVIS"})

        p1 (dc/platform {:short-name "platform_1" :instruments [i1 i7]})
        p2 (dc/platform {:short-name "platform_2" :instruments [i2 i8]})
        p3 (dc/platform {:short-name "platform_3" :instruments [i3]})
        p4 (dc/platform {:short-name "platform_4" :instruments [i4]})
        p5 (dc/platform {:short-name "platform_5" :instruments [i1 i2]})
        p6 (dc/platform {:short-name "platform_6" :instruments [i5]})
        p7 (dc/platform {:short-name "platform_7" :instruments [i6]})
        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "PROV2" (dc/collection {:platforms [p7]}))
        coll9 (d/ingest "PROV2" (dc/collection {}))
        ;; Added to test SMAP ISO platform and instrument support
        coll10 (d/ingest-concept-with-metadata-file "data/iso_smap/sample_smap_iso_collection.xml"
                                                    {:provider-id "PROV1"
                                                     :concept-type :collection
                                                     :format-key :iso-smap})]

    (index/wait-until-indexed)

    (testing "search collections by instrument"
      (are [items instrument-sn options]
           (let [params (merge {:instrument instrument-sn}
                               (when options
                                 {"options[instrument]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [coll1 coll2 coll6] "instrument_Sn A" {}
           [coll7 coll8] "instrument_x" {}
           [] "BLAH" {}
           [coll10] "SMAP L-BAND RADAR" {}
           [coll10] ["SMAP L-BAND RADAR" "SMAP L-BAND RADIOMETER"] {}
           [coll1 coll2 coll4 coll6] ["instrument_SnA" "instrument_Sn A"] {}
           [coll7 coll8] ["instrument_x"] {:ignore-case true}
           [coll8] ["instrument_x"] {:ignore-case false}
           [coll1 coll2 coll3 coll6] ["instrument_Sn *"] {:pattern true}
           [coll4 coll5] ["instrument_Sn?"] {:pattern true}
           [coll2 coll6] ["instrument_Sn B" "instrument_Sn A"] {:and true}))

    (testing "search collections by instrument with aql"
      (are [items instruments options]
           (let [condition (merge {:instrumentShortName instruments} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1 coll2 coll6] "instrument_Sn A" {}
           [coll8] "instrument_x" {}
           [] "BLAH" {}
           [coll1 coll2 coll4 coll6] ["instrument_SnA" "instrument_Sn A"] {}
           [coll7 coll8] ["instrument_x"] {:ignore-case true}
           [coll8] ["instrument_x"] {:ignore-case false}
           [coll1 coll2 coll3 coll6] "instrument_Sn %" {:pattern true}
           [coll4 coll5] "instrument_Sn_" {:pattern true}
           [coll2 coll6] ["instrument_Sn B" "instrument_Sn A"] {:and true}))

    (testing "Search collections by instrument using JSON query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll1 coll2 coll6] {:instrument {:short_name "instrument_Sn A"}}
           [coll7 coll8] {:instrument {:short_name "instrument_x"}}
           [] {:instrument {:short_name "BLAH"}}
           [coll10] {:instrument {:short_name "SMAP L-BAND RADAR"}}
           [coll10] {:and [{:instrument {:short_name "SMAP L-BAND RADAR"}}
                           {:instrument {:short_name "SMAP L-BAND RADIOMETER"}}]}
           [coll1 coll2 coll4 coll6] {:or [{:instrument {:short_name "instrument_SnA"}}
                                           {:instrument {:short_name "instrument_Sn A"}}]}
           [coll2 coll6] {:and [{:instrument {:short_name "instrument_Sn B"}}
                                {:instrument {:short_name "instrument_Sn A"}}]}
           [coll7 coll8] {:instrument {:short_name "instrument_x" :ignore_case true}}
           [coll8] {:instrument {:short_name "instrument_x" :ignore_case false}}
           [coll1 coll2 coll3 coll6] {:instrument {:short_name "instrument_Sn *" :pattern true}}
           [coll4 coll5] {:instrument {:short_name "instrument_Sn?" :pattern true}}

           ;; Test searching on KMS subfields
           [coll1 coll2 coll3 coll10] {:instrument {:category "Earth Remote Sensing Instruments"
                                                    :ignore_case false}}
           [] {:instrument {:category "EARTH REMOTE SENSING INSTRUMENTS" :ignore_case false}}
           [coll1 coll2 coll3 coll10] {:instrument {:category "EARTH REMOTE SENSING INSTRUMENTS"
                                                    :ignore_case true}}
           [coll1 coll2 coll3] {:instrument {:class "Active Remote Sensing"}}
           [coll1 coll2 coll3] {:instrument {:type "Altimeters"}}
           [coll10] {:instrument {:subtype "Imaging Spectrometers/Radiometers"}}

           ;; Short name uses KMS case rather than metadata case
           [] {:instrument {:short_name "atm" :ignore_case false}}
           [coll1 coll2] {:instrument {:short_name "atm" :ignore_case true}}
           [coll1 coll2] {:instrument {:short_name "ATM" :ignore_case false}}

           [coll2 coll3] {:instrument {:long_name "Land, V?getation*nd Ice Se?sor" :pattern true}}
           [] {:instrument {:long_name "Land, V?getation*nd Ice Se?sor"}}
           [coll1 coll2] {:instrument {:uuid "c2428a35-a87c-4ec7-aefd-13ff410b3271"}}
           [coll1 coll2] {:instrument {:any "c2428a35*" :pattern true}}))))

(deftest search-by-sensor-short-names
  (let [s1 (dc/sensor {:short-name "sensor_Sn A"})
        s2 (dc/sensor {:short-name "sensor_Sn B"})
        s3 (dc/sensor {:short-name "sensor_SnA"})
        s4 (dc/sensor {:short-name "sensor_Snx"})
        s5 (dc/sensor {:short-name "sensor_x"})
        s6 (dc/sensor {:short-name "SenSOR_X"})
        i1 (dc/instrument {:short-name "instrument_1" :sensors [s1]})
        i2 (dc/instrument {:short-name "instrument_2" :sensors [s2]})
        i3 (dc/instrument {:short-name "instrument_3" :sensors [s3]})
        i4 (dc/instrument {:short-name "instrument_4" :sensors [s4]})
        i5 (dc/instrument {:short-name "instrument_5" :sensors [s1 s2]})
        i6 (dc/instrument {:short-name "instrument_6" :sensors [s5]})
        i7 (dc/instrument {:short-name "instrument_7" :sensors [s6]})
        p1 (dc/platform {:short-name "platform_1" :instruments [i1]})
        p2 (dc/platform {:short-name "platform_2" :instruments [i2]})
        p3 (dc/platform {:short-name "platform_3" :instruments [i3]})
        p4 (dc/platform {:short-name "platform_4" :instruments [i4]})
        p5 (dc/platform {:short-name "platform_5" :instruments [i5]})
        p6 (dc/platform {:short-name "platform_6" :instruments [i1 i2]})
        p7 (dc/platform {:short-name "platform_7" :instruments [i6]})
        p8 (dc/platform {:short-name "platform_8" :instruments [i7]})
        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "PROV2" (dc/collection {:platforms [p6]}))
        coll8 (d/ingest "PROV2" (dc/collection {:platforms [p7]}))
        coll9 (d/ingest "PROV2" (dc/collection {:platforms [p8]}))
        coll10 (d/ingest "PROV2" (dc/collection {}))]

    (index/wait-until-indexed)

    (testing "search collections by sensor"
      (are [items sensor-sn options]
           (let [params (merge {:sensor sensor-sn}
                               (when options
                                 {"options[sensor]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [coll1 coll2 coll6 coll7] "sensor_Sn A" {}
           [coll8 coll9] "sensor_x" {}
           [] "BLAH" {}
           [coll1 coll2 coll4 coll6 coll7] ["sensor_SnA" "sensor_Sn A"] {}
           [coll8 coll9] ["sensor_x"] {:ignore-case true}
           [coll8] ["sensor_x"] {:ignore-case false}
           [coll1 coll2 coll3 coll6 coll7] ["sensor_Sn *"] {:pattern true}
           [coll4 coll5] ["sensor_Sn?"] {:pattern true}
           [coll2 coll6 coll7] ["sensor_Sn B" "sensor_Sn A"] {:and true}))

    (testing "search collections by sensor with aql"
      (are [items sensors options]
           (let [condition (merge {:sensorName sensors} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [coll1 coll2 coll6 coll7] "sensor_Sn A" {}
           [coll8] "sensor_x" {}
           [] "BLAH" {}
           [coll1 coll2 coll4 coll6 coll7] ["sensor_SnA" "sensor_Sn A"] {}
           [coll8 coll9] ["sensor_x"] {:ignore-case true}
           [coll8] ["sensor_x"] {:ignore-case false}
           [coll1 coll2 coll3 coll6 coll7] "sensor_Sn %" {:pattern true}
           [coll4 coll5] "sensor_Sn_" {:pattern true}
           [coll2 coll6 coll7] ["sensor_Sn B" "sensor_Sn A"] {:and true}))

    (testing "Search collections by sensor with JSON query"
      (are [items search]
             (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll1 coll2 coll6 coll7] {:sensor "sensor_Sn A"}
           [coll8 coll9] {:sensor "sensor_x"}
           [] {:sensor "BLAH"}
           [coll1 coll2 coll4 coll6 coll7] {:or [{:sensor "sensor_SnA"} {:sensor "sensor_Sn A"}]}
           [coll2 coll6 coll7] {:and [{:sensor "sensor_Sn B"} {:sensor "sensor_Sn A"}]}
           [coll8 coll9] {:sensor {:value "sensor_x" :ignore_case true}}
           [coll8] {:sensor {:value "sensor_x" :ignore_case false}}
           [coll1 coll2 coll3 coll6 coll7] {:sensor {:value "sensor_Sn *" :pattern true}}
           [coll4 coll5] {:sensor {:value "sensor_Sn?" :pattern true}}))))


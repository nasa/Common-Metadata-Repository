(ns cmr.system-int-test.search.collection-platform-instrument-sensor-search-test
  "Integration test for CMR collection search by platform, instrument and sensor short-names"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-platform
  (let [p1 (data-umm-cmn/platform {:ShortName "platform_Sn A"})
        p2 (data-umm-cmn/platform {:ShortName "platform_Sn B"})
        p3 (data-umm-cmn/platform {:ShortName "platform_SnA"})
        p4 (data-umm-cmn/platform {:ShortName "platform_Snx"})
        p5 (data-umm-cmn/platform {:ShortName "PLATFORM_X"})
        p6 (data-umm-cmn/platform {:ShortName "platform_x"})

        ;; Platforms to verify the ability to search by KMS platform subfields
        p7 (data-umm-cmn/platform {:ShortName "DMSP 5B/F3"})
        p8 (data-umm-cmn/platform {:ShortName "diaDEM-1d"})

        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1 p7]
                                                                            :EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1 p2 p8]
                                                                            :EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p2]
                                                                            :EntryTitle "E3"
                                                                            :ShortName "S3"
                                                                            :Version "V3"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p3]
                                                                            :EntryTitle "E4"
                                                                            :ShortName "S4"
                                                                            :Version "V4"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p4]
                                                                            :EntryTitle "E5"
                                                                            :ShortName "S5"
                                                                            :Version "V5"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p5]
                                                                            :EntryTitle "E6"
                                                                            :ShortName "S6"
                                                                            :Version "V6"}))
        coll7 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p6]
                                                                            :EntryTitle "E7"
                                                                            :ShortName "S7"
                                                                            :Version "V7"}))
        coll8 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E8"
                                                                            :ShortName "S8"
                                                                            :Version "V8"}))
        ;; Added to test SMAP ISO platform and instrument support - note that this collection is
        ;; found in KMS with a category of "Earth Observation Satellites"
        coll9 (d/ingest-concept-with-metadata-file "example-data/iso-smap/SMAPExample.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :format-key :iso-smap
                                                    :EntryTitle "E9"
                                                    :ShortName "S9"
                                                    :Version "V9"})]

    (index/wait-until-indexed)

    (doseq [field [:platform :platform-h]]
      (testing (str "Testing collection search by " (name field))
        (are3 [items platform-sn options]
              (let [params (merge {field platform-sn}
                                  (when options
                                    {(str "options[" (name field) "]") options}))]
                (d/refs-match? items (search/find-refs :collection params)))

              "search with one platform case1"
              [coll1 coll2] "platform_Sn A" {}

              "search with one platform case2"
              [coll6 coll7] "platform_x" {}

              "search with non existing platform return nothing"
              [] "BLAH" {}

              "search with one smap platform"
              [coll1 coll9] "DMSP 5B/F3" {}

              "search for collections with either platform1 or platform2"
              [coll1 coll2 coll4] ["platform_SnA" "platform_Sn A"] {}

              "search with one platform with ignore-case being true"
              [coll6 coll7] ["platform_x"] {:ignore-case true}

              "search with one platform with ignore-case being false"
              [coll7] ["platform_x"] {:ignore-case false}

              "search with platform containing pattern case1"
              [coll1 coll2 coll3] ["platform_Sn *"] {:pattern true}

              "search with platform containing pattern case2"
              [coll4 coll5] ["platform_Sn?"] {:pattern true}

              "search for collections with both platform1 and platform2"
              [coll2] ["platform_Sn B" "platform_Sn A"] {:and true})))


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
      (are3 [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           "Search collections by platform using JSON query test1"
           [coll1 coll2] {:platform {:short_name "platform_Sn A"}}
           "Search collections by platform using JSON query test2"
           [coll6 coll7] {:platform {:short_name "platform_x"}}
           "Search collections by platform using JSON query test3"
           [] {:platform {:short_name "BLAH"}}
           "Search collections by platform using JSON query test4"
           [coll1 coll9] {:platform {:short_name "DMSP 5B/F3"}}
           "Search collections by platform using JSON query test5"
           [coll1 coll2 coll4] {:or [{:platform {:short_name "platform_SnA"}}
                                     {:platform {:short_name "platform_Sn A"}}]}
           "Search collections by platform using JSON query test6"
           [coll2] {:and [{:platform {:short_name "platform_Sn B"}}
                          {:platform {:short_name "platform_Sn A"}}]}
           "Search collections by platform using JSON query test7"
           [coll6 coll7] {:platform {:short_name "platform_x" :ignore_case true}}
           "Search collections by platform using JSON query test8"
           [coll7] {:platform {:short_name "platform_x" :ignore_case false}}
           "Search collections by platform using JSON query test9"
           [coll1 coll2 coll3] {:platform {:short_name "platform_Sn *" :pattern true}}
           "Search collections by platform using JSON query test10"
           [coll4 coll5] {:platform {:short_name "platform_Sn?" :pattern true}}

           ;; Test searching on KMS subfields
           "Search collections by platform using JSON query test11"
           [coll1 coll2 coll9] {:platform {:category "Earth Observation Satellites"
                                           :ignore_case false}}
           "Search collections by platform using JSON query test12"
           [] {:platform {:category "EARTH OBSERVATION SATELLITES" :ignore_case false}}
           "Search collections by platform using JSON query test13"
           [coll1 coll2 coll9] {:platform {:category "EARTH OBSERVATION SATELLITES"
                                           :ignore_case true}}
           "Search collections by platform using JSON query test15"
           [] {:platform {:short_name "diaDEM-1d" :ignore_case false}}
           "Search collections by platform using JSON query test16"
           [coll2] {:platform {:short_name "diaDEM-1d" :ignore_case true}}
           "Search collections by platform using JSON query test17"
           [coll2] {:platform {:short_name "DIADEM-1D" :ignore_case false}}
           "Search collections by platform using JSON query test18"
           [coll1 coll9] {:platform {:long_name "defense METEOR*cal S?tellite *" :pattern true}}
           "Search collections by platform using JSON query test19"
           [] {:platform {:long_name "defense METEOR*cal S?tellite *"}}
           "Search collections by platform using JSON query test20"
           [coll1 coll9] {:platform {:uuid "7ed12e98-95b1-406c-a58a-f4bbfa405269"}}
           "Search collections by platform using JSON query test21"
           [coll1 coll9] {:platform {:any "7ed12e98*" :pattern true}}))))

(deftest search-by-instrument
  (let [i1 (data-umm-cmn/instrument {:ShortName "instrument_Sn A"})
        i2 (data-umm-cmn/instrument {:ShortName "instrument_Sn B"})
        i3 (data-umm-cmn/instrument {:ShortName "instrument_SnA"})
        i4 (data-umm-cmn/instrument {:ShortName "instrument_Snx"})
        i5 (data-umm-cmn/instrument {:ShortName "InstruMENT_X"})
        i6 (data-umm-cmn/instrument {:ShortName "instrument_x"})

        ;; Instruments to verify the ability to search by KMS instrument subfields
        i7 (data-umm-cmn/instrument {:ShortName "atm"})
        i8 (data-umm-cmn/instrument {:ShortName "LVIS"})

        p1 (data-umm-cmn/platform {:ShortName "platform_1" :Instruments [i1 i7]})
        p2 (data-umm-cmn/platform {:ShortName "platform_2" :Instruments [i2 i8]})
        p3 (data-umm-cmn/platform {:ShortName "platform_3" :Instruments [i3]})
        p4 (data-umm-cmn/platform {:ShortName "platform_4" :Instruments [i4]})
        p5 (data-umm-cmn/platform {:ShortName "platform_5" :Instruments [i1 i2]})
        p6 (data-umm-cmn/platform {:ShortName "platform_6" :Instruments [i5]})
        p7 (data-umm-cmn/platform {:ShortName "platform_7" :Instruments [i6]})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1]
                                                                            :EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1 p2]
                                                                            :EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p2]
                                                                            :EntryTitle "E3"
                                                                            :ShortName "S3"
                                                                            :Version "V3"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p3]
                                                                            :EntryTitle "E4"
                                                                            :ShortName "S4"
                                                                            :Version "V4"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p4]
                                                                            :EntryTitle "E5"
                                                                            :ShortName "S5"
                                                                            :Version "V5"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p5]
                                                                            :EntryTitle "E6"
                                                                            :ShortName "S6"
                                                                            :Version "V6"}))
        coll7 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p6]
                                                                            :EntryTitle "E7"
                                                                            :ShortName "S7"
                                                                            :Version "V7"}))
        coll8 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p7]
                                                                            :EntryTitle "E8"
                                                                            :ShortName "S8"
                                                                            :Version "V8"}))
        coll9 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E9"
                                                                            :ShortName "S9"
                                                                            :Version "V9"}))
        ;; Added to test SMAP ISO platform and instrument support
        coll10 (d/ingest-concept-with-metadata-file "example-data/iso-smap/SMAPExample.xml"
                                                   {:provider-id "PROV1"
                                                    :concept-type :collection
                                                    :format-key :iso-smap
                                                    :EntryTitle "E10"
                                                    :ShortName "S10"
                                                    :Version "V10"})]
    (index/wait-until-indexed)

    (doseq [field [:instrument :instrument-h]]
      (testing (str "Testing collection search by " (name field))
        (are3 [items instrument-sn options]
              (let [params (merge {field instrument-sn}
                                  (when options
                                    {(str "options[" (name field) "]") options}))]
                (d/refs-match? items (search/find-refs :collection params)))

              "search with one instrument case1"
              [coll1 coll2 coll6] "instrument_Sn A" {}

              "search with one instrument case2"
              [coll7 coll8] "instrument_x" {}

              "search with non-existing instrument"
              [] "BLAH" {}

              "search with one instrument case3"
              [coll1 coll2 coll10] "ATM" {}

              "search for collections with either instrument1 or instrument2 case1"
              [coll1 coll2 coll3 coll10] ["ATM" "LVIS"] {}

              "search for collections with either instrument1 or instrument2 case2"
              [coll1 coll2 coll4 coll6] ["instrument_SnA" "instrument_Sn A"] {}

              "search with one instrument with ingore-case being true"
              [coll7 coll8] ["instrument_x"] {:ignore-case true}

              "search with one instruement with ignore-case being false"
              [coll8] ["instrument_x"] {:ignore-case false}

              "search with instrument containing pattern case1"
              [coll1 coll2 coll3 coll6] ["instrument_Sn *"] {:pattern true}

              "search with instrument containing pattern case2"
              [coll4 coll5] ["instrument_Sn?"] {:pattern true}

              "search for collections with both instrument1 and instrument2"
              [coll2 coll6] ["instrument_Sn B" "instrument_Sn A"] {:and true})))

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
      (are3 [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           "Search collections by instrument using JSON query test1"
           [coll1 coll2 coll6] {:instrument {:short_name "instrument_Sn A"}}
           "Search collections by instrument using JSON query test2"
           [coll7 coll8] {:instrument {:short_name "instrument_x"}}
           "Search collections by instrument using JSON query test3"
           [] {:instrument {:short_name "BLAH"}}
           "Search collections by instrument using JSON query test4"
           [coll1 coll2 coll10] {:instrument {:short_name "ATM"}}
           "Search collections by instrument using JSON query test5"
           [coll2 coll10] {:and [{:instrument {:short_name "ATM"}}
                                 {:instrument {:short_name "LVIS"}}]}
           "Search collections by instrument using JSON query test6"
           [coll1 coll2 coll4 coll6] {:or [{:instrument {:short_name "instrument_SnA"}}
                                           {:instrument {:short_name "instrument_Sn A"}}]}
           "Search collections by instrument using JSON query test7"
           [coll2 coll6] {:and [{:instrument {:short_name "instrument_Sn B"}}
                                {:instrument {:short_name "instrument_Sn A"}}]}
           "Search collections by instrument using JSON query test8"
           [coll7 coll8] {:instrument {:short_name "instrument_x" :ignore_case true}}
           "Search collections by instrument using JSON query test9"
           [coll8] {:instrument {:short_name "instrument_x" :ignore_case false}}
           "Search collections by instrument using JSON query test10"
           [coll1 coll2 coll3 coll6] {:instrument {:short_name "instrument_Sn *" :pattern true}}
           "Search collections by instrument using JSON query test11"
           [coll4 coll5] {:instrument {:short_name "instrument_Sn?" :pattern true}}

           ;; Test searching on KMS subfields
           "Search collections by instrument using JSON query test12"
           [coll1 coll2 coll3 coll10] {:instrument {:category "Earth Remote Sensing Instruments"
                                                    :ignore_case false}}
           "Search collections by instrument using JSON query test13"
           [] {:instrument {:category "EARTH REMOTE SENSING INSTRUMENTS" :ignore_case false}}
           "Search collections by instrument using JSON query test14"
           [coll1 coll2 coll3 coll10] {:instrument {:category "EARTH REMOTE SENSING INSTRUMENTS"
                                                    :ignore_case true}}
           "Search collections by instrument using JSON query test15"
           [coll1 coll2 coll3 coll10] {:instrument {:class "Active Remote Sensing"}}
           "Search collections by instrument using JSON query test16"
           [coll1 coll2 coll3 coll10] {:instrument {:type "Altimeters"}}
           "Search collections by instrument using JSON query test17"
           [coll1 coll2 coll3 coll10] {:instrument {:subtype "Lidar/Laser Altimeters"}}

           ;; Short name uses KMS case rather than metadata case
           "Search collections by instrument using JSON query test18"
           [] {:instrument {:short_name "atm" :ignore_case false}}
           "Search collections by instrument using JSON query test19"
           [coll1 coll2 coll10] {:instrument {:short_name "atm" :ignore_case true}}
           "Search collections by instrument using JSON query test20"
           [coll1 coll2 coll10] {:instrument {:short_name "ATM" :ignore_case false}}
           "Search collections by instrument using JSON query test21"
           [coll2 coll3 coll10] {:instrument {:long_name "Land, V?getation*nd Ice Se?sor" :pattern true}}
           "Search collections by instrument using JSON query test22"
           [] {:instrument {:long_name "Land, V?getation*nd Ice Se?sor"}}
           "Search collections by instrument using JSON query test23"
           [coll1 coll2 coll10] {:instrument {:uuid "c2428a35-a87c-4ec7-aefd-13ff410b3271"}}
           "Search collections by instrument using JSON query test24"
           [coll1 coll2 coll10] {:instrument {:any "c2428a35*" :pattern true}}))))

(deftest search-by-sensor-short-names
  (let [;; child instrument
        s1 (data-umm-cmn/instrument {:ShortName "sensor_Sn A"})
        s2 (data-umm-cmn/instrument {:ShortName "sensor_Sn B"})
        s3 (data-umm-cmn/instrument {:ShortName "sensor_SnA"})
        s4 (data-umm-cmn/instrument {:ShortName "sensor_Snx"})
        s5 (data-umm-cmn/instrument {:ShortName "sensor_x"})
        s6 (data-umm-cmn/instrument {:ShortName "SenSOR_X"})
        ;; instrument
        i1 (data-umm-cmn/instrument {:ShortName "instrument_1" :ComposedOf [s1]})
        i2 (data-umm-cmn/instrument {:ShortName "instrument_2" :ComposedOf [s2]})
        i3 (data-umm-cmn/instrument {:ShortName "instrument_3" :ComposedOf [s3]})
        i4 (data-umm-cmn/instrument {:ShortName "instrument_4" :ComposedOf [s4]})
        i5 (data-umm-cmn/instrument {:ShortName "instrument_5" :ComposedOf [s1 s2]})
        i6 (data-umm-cmn/instrument {:ShortName "instrument_6" :ComposedOf [s5]})
        i7 (data-umm-cmn/instrument {:ShortName "instrument_7" :ComposedOf [s6]})
        p1 (data-umm-cmn/platform {:ShortName "platform_1" :Instruments [i1]})
        p2 (data-umm-cmn/platform {:ShortName "platform_2" :Instruments [i2]})
        p3 (data-umm-cmn/platform {:ShortName "platform_3" :Instruments [i3]})
        p4 (data-umm-cmn/platform {:ShortName "platform_4" :Instruments [i4]})
        p5 (data-umm-cmn/platform {:ShortName "platform_5" :Instruments [i5]})
        p6 (data-umm-cmn/platform {:ShortName "platform_6" :Instruments [i1 i2]})
        p7 (data-umm-cmn/platform {:ShortName "platform_7" :Instruments [i6]})
        p8 (data-umm-cmn/platform {:ShortName "platform_8" :Instruments [i7]})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1]
                                                                            :EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p1 p2]
                                                                            :EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Platforms [p2]
                                                                            :EntryTitle "E3"
                                                                            :ShortName "S3"
                                                                            :Version "V3"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p3]
                                                                            :EntryTitle "E4"
                                                                            :ShortName "S4"
                                                                            :Version "V4"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p4]
                                                                            :EntryTitle "E5"
                                                                            :ShortName "S5"
                                                                            :Version "V5"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p5]
                                                                            :EntryTitle "E6"
                                                                            :ShortName "S6"
                                                                            :Version "V6"}))
        coll7 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p6]
                                                                            :EntryTitle "E7"
                                                                            :ShortName "S7"
                                                                            :Version "V7"}))
        coll8 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p7]
                                                                            :EntryTitle "E8"
                                                                            :ShortName "S8"
                                                                            :Version "V8"}))
        coll9 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Platforms [p8]
                                                                            :EntryTitle "E9"
                                                                            :ShortName "S9"
                                                                            :Version "V9"}))
        coll10 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E10"
                                                                             :ShortName "S10"
                                                                             :Version "V10"}))]

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

(deftest dif9-blank-platform-short-name
  (testing "Ingest of a DIF9 collection with a long name for a platform, but a blank short name is
           treated as not provided"
    (let [{:keys [status]} (d/ingest-concept-with-metadata-file "CMR-5490/5490_dif9_blank_platform_sn.xml"
                                                                {:concept-type :collection
                                                                 :provider-id "PROV1"
                                                                 :native-id "blank-platform-sn"
                                                                 :format-key :dif})]
      (is (= 201 status))
      (testing "Collection can be updated without an internal server error from the platform validation"
        (let [response (d/ingest-concept-with-metadata-file "CMR-5490/5490_dif9_blank_platform_sn.xml"
                                                            {:concept-type :collection
                                                             :provider-id "PROV1"
                                                             :native-id "blank-platform-sn"
                                                             :format-key :dif})]
          (is (= 200 (:status response)))
          (index/wait-until-indexed)
          (testing "Collection is indexed despite the blank platform short name"
            (d/refs-match? [response]
                           (search/find-refs :collection {}))))))))

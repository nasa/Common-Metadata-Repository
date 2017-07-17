(ns cmr.system-int-test.search.granule-platform-instrument-sensor-search-test
  "Integration test for CMR granule search by platform, instrument and sensor short-names"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-platform-short-names
  (let [p0 (data-umm-cmn/platform {:ShortName "platform-Inherit"})
        p1 (data-umm-cmn/platform {:ShortName "platform-Sn A"})
        p2 (data-umm-cmn/platform {:ShortName "platform-Sn B"})
        p3 (data-umm-cmn/platform {:ShortName "platform-SnA"})
        p4 (data-umm-cmn/platform {:ShortName "platform-Snx"})
        p5 (data-umm-cmn/platform {:ShortName "platform-ONE"})
        p6 (data-umm-cmn/platform {:ShortName "platform-x"})
        p7 (data-umm-cmn/platform {:ShortName "PLATform-X"})
        pr1 (dg/platform-ref {:short-name "platform-Sn A"})
        pr2 (dg/platform-ref {:short-name "platform-Sn B"})
        pr3 (dg/platform-ref {:short-name "platform-SnA"})
        pr4 (dg/platform-ref {:short-name "platform-Snx"})
        pr5 (dg/platform-ref {:short-name "platform-ONE"})
        pr6 (dg/platform-ref {:short-name "platform-x"})
        pr7 (dg/platform-ref {:short-name "PLATform-X"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:Platforms [p1 p2 p3 p4]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Platforms [p0 p5 p6 p7]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr5]}))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr6]}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr7]}))]

    (index/wait-until-indexed)

    (testing "search granules by platform"
      (are [items platform-sn options]
           (let [params (merge {:platform platform-sn}
                               (when options
                                 {"options[platform]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2] "platform-Sn A" {}
           [gran2 gran3] "platform-Sn B" {}
           [gran6 gran8 gran9] "platform-x" {}
           [] "BLAH" {}

           ;; search by platform, multiple values"
           [gran1 gran2 gran4] ["platform-SnA" "platform-Sn A"] {}
           ;; search by platform, inheritance
           [gran6] ["platform-Inherit"] {}
           ;; search by platform, ignore case true
           [gran6 gran8 gran9] ["platform-x"] {:ignore-case true}
           ;; search by platform, ignore case false
           [gran6 gran8] ["platform-x"] {:ignore-case false}
           ;; search by platform, wildcards
           [gran1 gran2 gran3] ["platform-Sn *"] {:pattern true}
           [gran4 gran5] ["platform-Sn?"] {:pattern true}
           [] ["platform-Sn?"] {}
           ;; search by platform, options :and
           [gran2] ["platform-Sn B" "platform-Sn A"] {:and true}))

    (testing "search granules by platform with aql"
      (are [items platform-sn options]
           (let [condition (merge {:sourceName platform-sn} options)
                 result (search/find-refs-with-aql :granule [condition])]
             (or (d/refs-match? items result)
                 (println (pr-str result))))

           [gran1 gran2] "platform-Sn A" {}
           [gran2 gran3] "platform-Sn B" {}
           [gran6 gran8] "platform-x" {}
           [] "BLAH" {}

           ;; search by platform, multiple values and single quotes
           [gran1 gran2 gran4] ["'platform-SnA'" "'platform-Sn A'"] {}
           ;; search by platform, inheritance
           [gran6] ["platform-Inherit"] {}
           ;; search by platform, ignore case true
           [gran6 gran8 gran9] ["platform-x"] {:ignore-case true}
           ;; search by platform, ignore case false
           [gran6 gran8] ["platform-x"] {:ignore-case false}
           ;; search by platform, wildcards
           [gran1 gran2 gran3] "platform-Sn %" {:pattern true}
           [gran4 gran5] "platform-Sn_" {:pattern true}
           [] ["platform-Sn?"] {}
           ;; search by platform, options :and
           [gran2] ["platform-Sn B" "platform-Sn A"] {:and true}))))

(deftest search-by-instrument-short-names
  (let [i0 (data-umm-cmn/instrument {:ShortName "instrument-Inherit"})
        i01 (data-umm-cmn/instrument {:ShortName "instrument-ONE"})
        p0 (data-umm-cmn/platform {:ShortName "collection_platform" :Instruments [i0 i01]})
        i1 (data-umm-cmn/instrument {:ShortName "instrument-Sn A"})
        i2 (data-umm-cmn/instrument {:ShortName "instrument-Sn b"})
        i3 (data-umm-cmn/instrument {:ShortName "instrument-SnA"})
        i4 (data-umm-cmn/instrument {:ShortName "instrument-Snx"})
        i5 (data-umm-cmn/instrument {:ShortName "instrument-ONE"})
        i6 (data-umm-cmn/instrument {:ShortName "instrument-x"})
        i7 (data-umm-cmn/instrument {:ShortName "InstruMENT-X"})
        ir1 (dg/instrument-ref {:short-name "instrument-Sn A"})
        ir2 (dg/instrument-ref {:short-name "instrument-Sn b"})
        ir3 (dg/instrument-ref {:short-name "instrument-SnA"})
        ir4 (dg/instrument-ref {:short-name "instrument-Snx"})
        ir5 (dg/instrument-ref {:short-name "instrument-ONE"})
        ir6 (dg/instrument-ref {:short-name "instrument-x"})
        ir7 (dg/instrument-ref {:short-name "InstruMENT-X"})
        p1 (data-umm-cmn/platform {:ShortName "platform-1" :Instruments [i1]})
        p2 (data-umm-cmn/platform {:ShortName "platform-2" :Instruments [i2]})
        p3 (data-umm-cmn/platform {:ShortName "platform-3" :Instruments [i3]})
        p4 (data-umm-cmn/platform {:ShortName "platform-4" :Instruments [i4]})
        p5 (data-umm-cmn/platform {:ShortName "platform-5" :Instruments [i1 i2]})
        p6 (data-umm-cmn/platform {:ShortName "platform-6" :Instruments [i5]})
        p7 (data-umm-cmn/platform {:ShortName "platform-7" :Instruments [i6]})
        p8 (data-umm-cmn/platform {:ShortName "platform-8" :Instruments [i7]})
        pr1 (dg/platform-ref {:short-name "platform-1" :instrument-refs [ir1]})
        pr2 (dg/platform-ref {:short-name "platform-2" :instrument-refs [ir2]})
        pr3 (dg/platform-ref {:short-name "platform-3" :instrument-refs [ir3]})
        pr4 (dg/platform-ref {:short-name "platform-4" :instrument-refs [ir4]})
        pr5 (dg/platform-ref {:short-name "platform-5" :instrument-refs [ir1 ir2]})
        pr6 (dg/platform-ref {:short-name "platform-6" :instrument-refs [ir5]})
        pr7 (dg/platform-ref {:short-name "platform-7" :instrument-refs [ir6]})
        pr8 (dg/platform-ref {:short-name "platform-8" :instrument-refs [ir7]})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:ShortName "SHORT1" :Platforms [p1 p2 p3 p4 p5]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Platforms [p0 p6 p7 p8]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran1" :platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran2" :platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran3" :platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran4" :platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran5" :platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "gran6" :platform-refs [pr5]}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "gran7" }))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "gran8" :platform-refs [pr6]}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "gran9" :platform-refs [pr7]}))
        gran10 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:granule-ur "gran10" :platform-refs [pr8]}))]

    (index/wait-until-indexed)

    (testing "collection resolving query simplifications"
      ;; This is necessary to test some simplifications the collection query resolver is doing to pass collection ids found
      ;; in top level collection id queries down to subconditions executing collection queries
      (are [items params]
           (d/refs-match? items
                          (search/find-refs :granule params))
           [gran1 gran2] {:instrument "instrument-Sn A"
                          :platform "platform-1"
                          :echo_collection_id (:concept-id coll1)}
           [gran1 gran2] {:instrument "instrument-Sn A"
                          :platform "platform-1"
                          :provider "PROV1"
                          :short-name "SHORT1"}
           [] {:instrument "instrument-Sn A"
               :provider "PROV2"}
           [] {:instrument "instrument-Sn A"
               :provider "PROV1"
               :short-name "SHORT2"}))

    (testing "search by instrument"
      (are [items instrument-sn options]
           (let [params (merge {:instrument instrument-sn}
                               (when options
                                 {"options[instrument]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran6] "instrument-Sn A" {}
           [gran4] "instrument-SnA" {}
           [gran7 gran9 gran10] "instrument-x" {}
           [] "BLAH" {}

           ;; search by instrument, multiple values
           [gran1 gran2 gran4 gran6] ["instrument-SnA" "instrument-Sn A"] {}
           ;; search by instrument, inheritance
           [gran7] ["instrument-Inherit"] {}
           ;; search by instrument, ignore case
           [gran7 gran9 gran10] ["instrument-x"] {:ignore-case true}
           [gran7 gran9] ["instrument-x"] {:ignore-case false}
           ;; search by instrument, wildcards
           [gran1 gran2 gran3 gran6] ["instrument-Sn *"] {:pattern true}
           [gran4 gran5] ["instrument-Sn?"] {:pattern true}
           ;; search by instrument, options :and
           [gran2 gran6] ["instrument-Sn b" "instrument-Sn A"] {:and true}))

    (testing "search granules by instrument with aql"
      (are [items instruments options]
           (let [condition (merge {:instrumentShortName instruments} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1 gran2 gran6] "instrument-Sn A" {}
           [gran4] "instrument-SnA" {}
           [gran7 gran9] "instrument-x" {}
           [] "BLAH" {}

           ;; search by instrument, multiple values
           [gran1 gran2 gran4 gran6] ["instrument-SnA" "instrument-Sn A"] {}
           ;; search by instrument, inheritance
           [gran7] ["instrument-Inherit"] {}
           ;; search by instrument, ignore case
           [gran7 gran9 gran10] ["instrument-x"] {:ignore-case true}
           [gran7 gran9] ["instrument-x"] {:ignore-case false}
           ;; search by instrument, wildcards
           [gran1 gran2 gran3 gran6] "instrument-Sn %" {:pattern true}
           [gran4 gran5] "instrument-Sn_" {:pattern true}
           ;; search by instrument, options :and
           [gran2 gran6] ["instrument-Sn b" "instrument-Sn A"] {:and true}))))

(deftest search-by-sensor-short-names
  (let [s0 (data-umm-cmn/instrument {:ShortName "sensor-Inherit"})
        s01 (data-umm-cmn/instrument {:ShortName "sensor-ONE"})
        i0 (data-umm-cmn/instrument {:ShortName "collection_instrument" :ComposedOf [s0 s01]})
        p0 (data-umm-cmn/platform {:ShortName "collection_platform" :Instruments [i0]})
        s1 (data-umm-cmn/instrument {:ShortName "sensor-Sn A"})
        s2 (data-umm-cmn/instrument {:ShortName "sensor-Sn b"})
        s3 (data-umm-cmn/instrument {:ShortName "sensor-SnA"})
        s4 (data-umm-cmn/instrument {:ShortName "sensor-Snx"})
        s5 (data-umm-cmn/instrument {:ShortName "sensor-ONE"})
        s6 (data-umm-cmn/instrument {:ShortName "sensor-x"})
        s7 (data-umm-cmn/instrument {:ShortName "SEnsor-X"})
        sr1 (dg/sensor-ref {:short-name "sensor-Sn A"})
        sr2 (dg/sensor-ref {:short-name "sensor-Sn b"})
        sr3 (dg/sensor-ref {:short-name "sensor-SnA"})
        sr4 (dg/sensor-ref {:short-name "sensor-Snx"})
        sr5 (dg/sensor-ref {:short-name "sensor-ONE"})
        sr6 (dg/sensor-ref {:short-name "sensor-x"})
        sr7 (dg/sensor-ref {:short-name "SEnsor-X"})
        i1 (data-umm-cmn/instrument {:ShortName "instrument-1" :ComposedOf [s1]})
        i2 (data-umm-cmn/instrument {:ShortName "instrument-2" :ComposedOf [s2]})
        i3 (data-umm-cmn/instrument {:ShortName "instrument-3" :ComposedOf [s3]})
        i4 (data-umm-cmn/instrument {:ShortName "instrument-4" :ComposedOf [s4]})
        i5 (data-umm-cmn/instrument {:ShortName "instrument-5" :ComposedOf [s1 s2]})
        i6 (data-umm-cmn/instrument {:ShortName "instrument-6" :ComposedOf [s5]})
        i7 (data-umm-cmn/instrument {:ShortName "instrument-7" :ComposedOf [s6]})
        i8 (data-umm-cmn/instrument {:ShortName "instrument-8" :ComposedOf [s7]})
        ir1 (dg/instrument-ref {:short-name "instrument-1" :sensor-refs [sr1]})
        ir2 (dg/instrument-ref {:short-name "instrument-2" :sensor-refs [sr2]})
        ir3 (dg/instrument-ref {:short-name "instrument-3" :sensor-refs [sr3]})
        ir4 (dg/instrument-ref {:short-name "instrument-4" :sensor-refs [sr4]})
        ir5 (dg/instrument-ref {:short-name "instrument-5" :sensor-refs [sr1 sr2]})
        ir6 (dg/instrument-ref {:short-name "instrument-6" :sensor-refs [sr5]})
        ir7 (dg/instrument-ref {:short-name "instrument-7" :sensor-refs [sr6]})
        ir8 (dg/instrument-ref {:short-name "instrument-8" :sensor-refs [sr7]})
        p1 (data-umm-cmn/platform {:ShortName "platform-1" :Instruments [i1]})
        p2 (data-umm-cmn/platform {:ShortName "platform-2" :Instruments [i2]})
        p3 (data-umm-cmn/platform {:ShortName "platform-3" :Instruments [i3]})
        p4 (data-umm-cmn/platform {:ShortName "platform-4" :Instruments [i4]})
        p5 (data-umm-cmn/platform {:ShortName "platform-5" :Instruments [i5]})
        p6 (data-umm-cmn/platform {:ShortName "platform-6" :Instruments [i1 i2]})
        p7 (data-umm-cmn/platform {:ShortName "platform-7" :Instruments [i6]})
        p8 (data-umm-cmn/platform {:ShortName "platform-8" :Instruments [i7]})
        p9 (data-umm-cmn/platform {:ShortName "platform-9" :Instruments [i8]})
        pr1 (dg/platform-ref {:short-name "platform-1" :instrument-refs [ir1]})
        pr2 (dg/platform-ref {:short-name "platform-2" :instrument-refs [ir2]})
        pr3 (dg/platform-ref {:short-name "platform-3" :instrument-refs [ir3]})
        pr4 (dg/platform-ref {:short-name "platform-4" :instrument-refs [ir4]})
        pr5 (dg/platform-ref {:short-name "platform-5" :instrument-refs [ir5]})
        pr6 (dg/platform-ref {:short-name "platform-6" :instrument-refs [ir1 ir2]})
        pr7 (dg/platform-ref {:short-name "platform-7" :instrument-refs [ir6]})
        pr8 (dg/platform-ref {:short-name "platform-8" :instrument-refs [ir7]})
        pr9 (dg/platform-ref {:short-name "platform-9" :instrument-refs [ir8]})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:Platforms [p1 p2 p3 p4 p5 p6]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:Platforms [p0 p7 p8 p9]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr5]}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:platform-refs [pr6]}))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr7]}))
        gran10 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr8]}))
        gran11 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 (:concept-id coll2) {:platform-refs [pr9]}))]

    (index/wait-until-indexed)

    (testing "search by sensor"
      (are [items sensor-sn options]
           (let [params (merge {:sensor sensor-sn}
                               (when options
                                 {"options[sensor]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran6 gran7] "sensor-Sn A" {}
           [gran4] "sensor-SnA" {}
           [gran8 gran10 gran11] "sensor-x" {}
           [] "BLAH" {}

           ;; search by sensor, multiple values
           [gran1 gran2 gran4 gran6 gran7] ["sensor-SnA" "sensor-Sn A"] {}
           ;; search by sensor, inheritance
           [gran8] ["sensor-Inherit"] {}
           ;; search by sensor, ignore case
           [gran8 gran10 gran11] ["sensor-x"] {:ignore-case true}
           [gran8 gran10] ["sensor-x"] {:ignore-case false}
           ;; search by sensor, wildcards
           [gran1 gran2 gran3 gran6 gran7] ["sensor-Sn *"] {:pattern true}
           [gran4 gran5] ["sensor-Sn?"] {:pattern true}
           ;; search by sensor, options :and
           [gran2 gran6 gran7] ["sensor-Sn b" "sensor-Sn A"] {:and true}))

    (testing "search granules by sensor with aql"
      (are [items sensors options]
           (let [condition (merge {:sensorName sensors} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1 gran2 gran6 gran7] "sensor-Sn A" {}
           [gran4] "sensor-SnA" {}
           [gran8 gran10] "sensor-x" {}
           [] "BLAH" {}

           ;; search by sensor, multiple values
           [gran1 gran2 gran4 gran6 gran7] ["sensor-SnA" "sensor-Sn A"] {}
           ;; search by sensor, inheritance
           [gran8] ["sensor-Inherit"] {}
           ;; search by sensor, ignore case
           [gran8 gran10 gran11] ["sensor-x"] {:ignore-case true}
           [gran8 gran10] ["sensor-x"] {:ignore-case false}
           ;; search by sensor, wildcards
           [gran1 gran2 gran3 gran6 gran7] "sensor-Sn %" {:pattern true}
           [gran4 gran5] "sensor-Sn_" {:pattern true}
           ;; search by sensor, options :and
           [gran2 gran6 gran7] ["sensor-Sn b" "sensor-Sn A"] {:and true}))))

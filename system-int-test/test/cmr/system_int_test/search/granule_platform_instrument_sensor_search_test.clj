(ns cmr.system-int-test.search.granule-platform-instrument-sensor-search-test
  "Integration test for CMR granule search by platform, instrument and sensor short-names"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-platform-short-names
  (let [p0 (dc/platform "platform-Inherit")
        p1 (dc/platform "platform-Sn A")
        p2 (dc/platform "platform-Sn B")
        p3 (dc/platform "platform-SnA")
        p4 (dc/platform "platform-Snx")
        p5 (dc/platform "platform-ONE")
        p6 (dc/platform "platform-x")
        p7 (dc/platform "PLATform-X")
        pr1 (dg/platform-ref "platform-Sn A")
        pr2 (dg/platform-ref "platform-Sn B")
        pr3 (dg/platform-ref "platform-SnA")
        pr4 (dg/platform-ref "platform-Snx")
        pr5 (dg/platform-ref "platform-ONE")
        pr6 (dg/platform-ref "platform-x")
        pr7 (dg/platform-ref "PLATform-X")
        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1 p2 p3 p4]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p0 p5 p6 p7]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule coll2 {}))
        gran7 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr5]}))
        gran8 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr6]}))
        gran9 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr7]}))]

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
  (let [i0 (dc/instrument {:short-name "instrument-Inherit"})
        i01 (dc/instrument {:short-name "instrument-ONE"})
        p0 (dc/platform "collection_platform" "dummy" nil i0 i01)
        i1 (dc/instrument {:short-name "instrument-Sn A"})
        i2 (dc/instrument {:short-name "instrument-Sn b"})
        i3 (dc/instrument {:short-name "instrument-SnA"})
        i4 (dc/instrument {:short-name "instrument-Snx"})
        i5 (dc/instrument {:short-name "instrument-ONE"})
        i6 (dc/instrument {:short-name "instrument-x"})
        i7 (dc/instrument {:short-name "InstruMENT-X"})
        ir1 (dg/instrument-ref "instrument-Sn A")
        ir2 (dg/instrument-ref "instrument-Sn b")
        ir3 (dg/instrument-ref "instrument-SnA")
        ir4 (dg/instrument-ref "instrument-Snx")
        ir5 (dg/instrument-ref "instrument-ONE")
        ir6 (dg/instrument-ref "instrument-x")
        ir7 (dg/instrument-ref "InstruMENT-X")
        p1 (dc/platform "platform-1" "dummy1" nil i1)
        p2 (dc/platform "platform-2" "dummy2" nil i2)
        p3 (dc/platform "platform-3" "dummy3" nil i3)
        p4 (dc/platform "platform-4" "dummy4" nil i4)
        p5 (dc/platform "platform-5" "dummy5" nil i1 i2)
        p6 (dc/platform "platform-6" "dummy6" nil i5)
        p7 (dc/platform "platform-7" "dummy7" nil i6)
        p8 (dc/platform "platform-8" "dummy8" nil i7)
        pr1 (dg/platform-ref "platform-1" ir1)
        pr2 (dg/platform-ref "platform-2" ir2)
        pr3 (dg/platform-ref "platform-3" ir3)
        pr4 (dg/platform-ref "platform-4" ir4)
        pr5 (dg/platform-ref "platform-5" ir1 ir2)
        pr6 (dg/platform-ref "platform-6" ir5)
        pr7 (dg/platform-ref "platform-7" ir6)
        pr8 (dg/platform-ref "platform-8" ir7)
        coll1 (d/ingest "PROV1" (dc/collection {:short-name "SHORT1" :platforms [p1 p2 p3 p4 p5]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p0 p6 p7 p8]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran1" :platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran2" :platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran3" :platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran4" :platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran5" :platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "gran6" :platform-refs [pr5]}))
        gran7 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran7" }))
        gran8 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran8" :platform-refs [pr6]}))
        gran9 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran9" :platform-refs [pr7]}))
        gran10 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "gran10" :platform-refs [pr8]}))]

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
  (let [s0 (dc/sensor {:short-name "sensor-Inherit"})
        s01 (dc/sensor {:short-name "sensor-ONE"})
        i0 (dc/instrument {:short-name "collection_instrument" :sensors [s0 s01]})
        p0 (dc/platform "collection_platform" "collection_platform" nil i0)
        s1 (dc/sensor {:short-name "sensor-Sn A"})
        s2 (dc/sensor {:short-name "sensor-Sn b"})
        s3 (dc/sensor {:short-name "sensor-SnA"})
        s4 (dc/sensor {:short-name "sensor-Snx"})
        s5 (dc/sensor {:short-name "sensor-ONE"})
        s6 (dc/sensor {:short-name "sensor-x"})
        s7 (dc/sensor {:short-name "SEnsor-X"})
        sr1 (dg/sensor-ref "sensor-Sn A")
        sr2 (dg/sensor-ref "sensor-Sn b")
        sr3 (dg/sensor-ref "sensor-SnA")
        sr4 (dg/sensor-ref "sensor-Snx")
        sr5 (dg/sensor-ref "sensor-ONE")
        sr6 (dg/sensor-ref "sensor-x")
        sr7 (dg/sensor-ref "SEnsor-X")
        i1 (dc/instrument {:short-name "instrument-1" :sensors [s1]})
        i2 (dc/instrument {:short-name "instrument-2" :sensors [s2]})
        i3 (dc/instrument {:short-name "instrument-3" :sensors [s3]})
        i4 (dc/instrument {:short-name "instrument-4" :sensors [s4]})
        i5 (dc/instrument {:short-name "instrument-5" :sensors [s1 s2]})
        i6 (dc/instrument {:short-name "instrument-6" :sensors [s5]})
        i7 (dc/instrument {:short-name "instrument-7" :sensors [s6]})
        i8 (dc/instrument {:short-name "instrument-8" :sensors [s7]})
        ir1 (dg/instrument-ref "instrument-1" sr1)
        ir2 (dg/instrument-ref "instrument-2" sr2)
        ir3 (dg/instrument-ref "instrument-3" sr3)
        ir4 (dg/instrument-ref "instrument-4" sr4)
        ir5 (dg/instrument-ref "instrument-5" sr1 sr2)
        ir6 (dg/instrument-ref "instrument-6" sr5)
        ir7 (dg/instrument-ref "instrument-7" sr6)
        ir8 (dg/instrument-ref "instrument-8" sr7)
        p1 (dc/platform "platform-1" "dummy" nil i1)
        p2 (dc/platform "platform-2" "dummy" nil i2)
        p3 (dc/platform "platform-3" "dummy" nil i3)
        p4 (dc/platform "platform-4" "dummy" nil i4)
        p5 (dc/platform "platform-5" "dummy" nil i5)
        p6 (dc/platform "platform-6" "dummy" nil i1 i2)
        p7 (dc/platform "platform-7" "dummy" nil i6)
        p8 (dc/platform "platform-8" "dummy" nil i7)
        p9 (dc/platform "platform-9" "dummy" nil i8)
        pr1 (dg/platform-ref "platform-1" ir1)
        pr2 (dg/platform-ref "platform-2" ir2)
        pr3 (dg/platform-ref "platform-3" ir3)
        pr4 (dg/platform-ref "platform-4" ir4)
        pr5 (dg/platform-ref "platform-5" ir5)
        pr6 (dg/platform-ref "platform-6" ir1 ir2)
        pr7 (dg/platform-ref "platform-7" ir6)
        pr8 (dg/platform-ref "platform-8" ir7)
        pr9 (dg/platform-ref "platform-9" ir8)
        coll1 (d/ingest "PROV1" (dc/collection {:platforms [p1 p2 p3 p4 p5 p6]}))
        coll2 (d/ingest "PROV1" (dc/collection {:platforms [p0 p7 p8 p9]}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr1]}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr1 pr2]}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr2]}))
        gran4 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr3]}))
        gran5 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr4]}))
        gran6 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr5]}))
        gran7 (d/ingest "PROV1" (dg/granule coll1 {:platform-refs [pr6]}))
        gran8 (d/ingest "PROV1" (dg/granule coll2 {}))
        gran9 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr7]}))
        gran10 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr8]}))
        gran11 (d/ingest "PROV1" (dg/granule coll2 {:platform-refs [pr9]}))]

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

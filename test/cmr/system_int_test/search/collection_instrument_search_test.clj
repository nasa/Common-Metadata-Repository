(ns cmr.system-int-test.search.collection-instrument-search-test
  "Integration test for CMR collection search by instrument short-names"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-instrument-short-names
  (let [i1 (dc/instrument "instrument_Sn A")
        i2 (dc/instrument "instrument_Sn a")
        i3 (dc/instrument "instrument_SnA")
        i4 (dc/instrument "instrument_Snx")
        p1 (dc/platform "platform_1" [i1])
        p2 (dc/platform "platform_2" [i2])
        p3 (dc/platform "platform_3" [i3])
        p4 (dc/platform "platform_4" [i4])
        p5 (dc/platform "platform_5" [i1 i2])
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p1 p2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:platforms [p2]}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p3]}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p4]}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:platforms [p5]}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {}))]

    (index/flush-elastic-index)

    (testing "search by platfrom, single value"
      (are [instrument-sn items] (d/refs-match? items (search/find-refs :collection {:instrument instrument-sn}))
           "instrument_Sn A" [coll1 coll2 coll6]
           "BLAH" []))
    (testing "search by platfrom, multiple values"
      (is (d/refs-match? [coll1 coll2 coll4 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_SnA" "instrument_Sn A"]}))))
    (testing "search by platfrom, ignore case true"
      (is (d/refs-match? [coll1 coll2 coll3 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn A"]
                                                        "options[instrument][ignore-case]" "true"}))))
    (testing "search by platfrom, ignore case false"
      (is (d/refs-match? [coll1 coll2 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn A"]
                                                        "options[instrument][ignore-case]" "false"}))))
    (testing "search by platfrom, wildcard *"
      (is (d/refs-match? [coll1 coll2 coll3 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn *"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by platfrom, wildcard ?"
      (is (d/refs-match? [coll4 coll5]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn?"]
                                                        "options[instrument][pattern]" "true"}))))
    (testing "search by platfrom, options :or."
      (is (d/refs-match? [coll1 coll2 coll3 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn a" "instrument_Sn A"]
                                                        "options[instrument][or]" "true"}))))
    (testing "search by platfrom, options :and."
      (is (d/refs-match? [coll2 coll6]
                         (search/find-refs :collection {"instrument[]" ["instrument_Sn a" "instrument_Sn A"]
                                                        "options[instrument][and]" "true"}))))))

(ns ^{:doc "Search CMR granules by day night flag"}
  cmr.system-int-test.search.granule-search-day-night-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))


(deftest search-by-day-night
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:day-night "DAY"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:day-night "NIGHT"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:day-night "BOTH"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran6 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran7 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran8 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran9 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran10 (d/ingest "CMR_PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))]
    (index/flush-elastic-index)
    (testing "search by invalid day-night flag."
      (let [refs (search/find-refs :granule {:day-night "FAKE"})]
        (is (d/refs-match? [] refs))))
    (testing "search by day-night does not ignore case."
      (let [refs (search/find-refs :granule {:day-night "nIgHt"})]
        (is (d/refs-match? [] refs))))
    (testing "search by day-night does not ignore case when specifying ignore case false."
      (let [refs (search/find-refs :granule {:day-night "nIgHt"
                                             "options[day-night][ignore-case]" "false"})]
        (is (d/refs-match? [] refs))))
    (testing "search by day-night ignores case when specifying ignore case true."
      (let [refs (search/find-refs :granule {:day-night "nIgHt"
                                             "options[day-night][ignore-case]" "true"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night DAY."
      (let [refs (search/find-refs :granule {:day-night "DAY"})]
        (is (d/refs-match? [gran1 gran5 gran6] refs))))
    (testing "search by day-night NIGHT."
      (let [refs (search/find-refs :granule {:day-night "NIGHT"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night BOTH"
      (let [refs (search/find-refs :granule {:day-night "BOTH"})]
        (is (d/refs-match? [gran3 gran7 gran8 gran9] refs))))
    (testing "search by day-night UNSPECIFIED"
      (let [refs (search/find-refs :granule {:day-night "UNSPECIFIED"})]
        (is (d/refs-match? [gran4 gran10] refs))))
    (testing "search by multiple day-night flags."
      (let [refs (search/find-refs :granule {"day-night[]" ["DAY", "BOTH", "UNSPECIFIED"]})]
        (is (d/refs-match? [gran1 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10] refs))))
    (testing "search by day-night using wildcard *."
      (let [refs (search/find-refs :granule
                                   {:day-night "*SP*C*"
                                    "options[day-night][pattern]" "true"})]
        (is (d/refs-match? [gran4 gran10] refs))))
    (testing "search by day-night using wildcard ?."
      (let [refs (search/find-refs :granule
                                   {:day-night "?I?H?"
                                    "options[day-night][pattern]" "true"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night using :or option."
      (let [refs (search/find-refs :granule {"day-night[]" ["DAY", "BOTH", "UNSPECIFIED"]
                                             "options[day-night][or]" "true"})]
        (is (d/refs-match? [gran1 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10] refs))))
    (testing "search by readable granule name options :and does not return results."
      (let [refs (search/find-refs :granule
                                   {:day-night ["DAY", "NIGHT"]
                                    "options[day-night][and]" "true"})]
        (is (d/refs-match? [] refs))))))

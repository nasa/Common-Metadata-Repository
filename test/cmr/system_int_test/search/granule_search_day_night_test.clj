(ns ^{:doc "Search CMR granules by day night flag"}
  cmr.system-int-test.search.granule-search-day-night-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))


(deftest search-by-day-night
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:day-night "DAY"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:day-night "NIGHT"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:day-night "BOTH"}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))
        gran5 (d/ingest "PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran6 (d/ingest "PROV2" (dg/granule coll2 {:day-night "DAY"}))
        gran7 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran8 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran9 (d/ingest "PROV2" (dg/granule coll2 {:day-night "BOTH"}))
        gran10 (d/ingest "PROV2" (dg/granule coll2 {:day-night "UNSPECIFIED"}))]
    (index/refresh-elastic-index)
    (testing "search by invalid day-night-flag."
      (let [refs (search/find-refs :granule {:day-night-flag "FAKE"})]
        (is (d/refs-match? [] refs))))
    (testing "search by day-night-flag default is ignore case true."
      (let [refs (search/find-refs :granule {:day-night-flag "nIgHt"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night-flag does not ignore case when specifying ignore case false."
      (let [refs (search/find-refs :granule {:day-night-flag "nIgHt"
                                             "options[day-night-flag][ignore-case]" "false"})]
        (is (d/refs-match? [] refs))))
    (testing "search by day-night-flag ignores case when specifying ignore case true."
      (let [refs (search/find-refs :granule {:day-night-flag "nIgHt"
                                             "options[day-night-flag][ignore-case]" "true"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night-flag DAY."
      (let [refs (search/find-refs :granule {:day-night-flag "DAY"})]
        (is (d/refs-match? [gran1 gran5 gran6] refs))))
    (testing "search by day-night-flag NIGHT."
      (let [refs (search/find-refs :granule {:day-night-flag "NIGHT"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night-flag BOTH"
      (let [refs (search/find-refs :granule {:day-night-flag "BOTH"})]
        (is (d/refs-match? [gran3 gran7 gran8 gran9] refs))))
    (testing "search by day-night-flag UNSPECIFIED"
      (let [refs (search/find-refs :granule {:day-night-flag "UNSPECIFIED"})]
        (is (d/refs-match? [gran4 gran10] refs))))
    (testing "search by multiple day-night-flag flags."
      (let [refs (search/find-refs :granule {"day-night-flag[]" ["DAY", "BOTH", "UNSPECIFIED"]})]
        (is (d/refs-match? [gran1 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10] refs))))
    (testing "search by day-night-flag using wildcard *."
      (let [refs (search/find-refs :granule
                                   {:day-night-flag "*SP*C*"
                                    "options[day-night-flag][pattern]" "true"})]
        (is (d/refs-match? [gran4 gran10] refs))))
    (testing "search by day-night-flag using wildcard ?."
      (let [refs (search/find-refs :granule
                                   {:day-night-flag "?I?H?"
                                    "options[day-night-flag][pattern]" "true"})]
        (is (d/refs-match? [gran2] refs))))
    (testing "search by day-night-flag using :or option."
      (let [refs (search/find-refs :granule {"day-night-flag[]" ["DAY", "BOTH", "UNSPECIFIED"]
                                             "options[day-night-flag][or]" "true"})]
        (is (d/refs-match? [gran1 gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10] refs))))
    (testing "search by day-night-flag options :and does not return results."
      (let [refs (search/find-refs :granule
                                   {:day-night-flag ["DAY", "NIGHT"]
                                    "options[day-night-flag][and]" "true"})]
        (is (d/refs-match? [] refs))))))

(ns ^{:doc "Integration test for CMR granule periodic temporal search"}
  cmr.system-int-test.search.granule-periodic-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-by-periodic-temporal
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                    :beginning-date-time "2000-01-01T12:00:00Z"
                                                    :ending-date-time "2000-02-14T12:00:00Z"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                    :beginning-date-time "2000-02-14T12:00:00Z"
                                                    :ending-date-time "2000-02-15T12:00:00Z"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule3"
                                                    :beginning-date-time "2000-03-15T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule4"
                                                    :beginning-date-time "2000-04-01T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule5"
                                                    :beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-01-31T12:00:00Z"}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule6"
                                                    :beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-02-14T12:00:00Z"}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule7"
                                                    :beginning-date-time "2001-03-15T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule8"
                                                    :beginning-date-time "2001-04-01T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        gran9 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule9"
                                                    :beginning-date-time "2002-01-01T12:00:00Z"
                                                    :ending-date-time "2002-01-31T12:00:00Z"}))
        gran10 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule10"
                                                     :beginning-date-time "2002-01-01T12:00:00Z"
                                                     :ending-date-time "2002-02-14T12:00:00Z"}))
        gran11 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule11"
                                                     :beginning-date-time "2002-03-14T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        gran12 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule12"
                                                     :beginning-date-time "2002-03-15T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        gran13 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule13"
                                                     :beginning-date-time "2002-04-01T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        gran14 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule14"
                                                     :beginning-date-time "1999-02-15T12:00:00Z"
                                                     :ending-date-time "1999-03-15T12:00:00Z"}))
        gran15 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule15"
                                                     :beginning-date-time "2003-02-15T12:00:00Z"
                                                     :ending-date-time "2003-03-15T12:00:00Z"}))
        gran16 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule16"
                                                     :beginning-date-time "1999-02-15T12:00:00Z"}))
        gran17 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule17"
                                                     :beginning-date-time "2001-02-15T12:00:00Z"}))
        gran18 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule18"
                                                     :beginning-date-time "2002-03-15T12:00:00Z"}))
        gran19 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule19"
                                                     :beginning-date-time "2001-11-15T12:00:00Z"
                                                     :ending-date-time "2001-12-15T12:00:00Z"}))
        gran20 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule20"}))]
    (index/flush-elastic-index)

    (testing "search by both start-day and end-day."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
                                          :page_size 100})]
        (is (d/refs-match? [gran2 gran3 gran6 gran7 gran10 gran11 gran16 gran17] references))))
    (testing "search by end-day."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, , 90"
                                          :page_size 100})]
        (is (d/refs-match?
              [gran2 gran3 gran5 gran6 gran7 gran9 gran10 gran11 gran16 gran17] references))))
    (testing "search by start-day."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32,"
                                          :page_size 100})]
        (is (d/refs-match?
              [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran16 gran17 gran19] references))))
    (testing "search by start-day without end_date."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2000-02-15T00:00:00Z, , 32"]
                                          :page_size 100})]
        (is (d/refs-match?
              [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran12 gran13 gran15 gran16 gran17 gran18 gran19]
              references))))
    (testing "search by start-day/end-day with date crossing year boundary."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2000-04-03T00:00:00Z, 2002-01-02T00:00:00Z, 93, 2"]
                                          :page_size 100})]
        (is (d/refs-match?
              [gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10 gran16 gran17 gran19] references))))
    (testing "search by multiple temporal."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["1998-01-15T00:00:00Z, 1999-03-15T00:00:00Z, 60, 90"
                                                        "2000-02-15T00:00:00Z, 2001-03-15T00:00:00Z, 40, 50"]
                                          :page_size 100})]
        (is (d/refs-match? [gran2 gran6 gran14 gran16 gran17] references))))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search with temporal_start_day and no temporal_start is invalid."
    (try
      (search/find-refs :granule {"temporal[]" ", , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_start_day must be accompanied by a temporal_start" body))))))
  (testing "search with temporal_end_day and no temporal_end is invalid."
    (try
      (search/find-refs :granule {"temporal[]" ", , , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_end_day must be accompanied by a temporal_end" body)))))))
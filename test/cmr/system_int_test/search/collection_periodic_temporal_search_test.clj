(ns ^{:doc "Integration test for CMR collection periodic temporal search"}
  cmr.system-int-test.search.collection-periodic-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-periodic-temporal
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset1"
                                                    :beginning-date-time "2000-01-01T12:00:00Z"
                                                    :ending-date-time "2000-02-14T12:00:00Z"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset2"
                                                    :beginning-date-time "2000-02-14T12:00:00Z"
                                                    :ending-date-time "2000-02-15T12:00:00Z"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset3"
                                                    :beginning-date-time "2000-03-15T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset4"
                                                    :beginning-date-time "2000-04-01T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        coll5 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset5"
                                                    :beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-01-31T12:00:00Z"}))
        coll6 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset6"
                                                    :beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-02-14T12:00:00Z"}))
        coll7 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset7"
                                                    :beginning-date-time "2001-03-15T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        coll8 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset8"
                                                    :beginning-date-time "2001-04-01T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        coll9 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset9"
                                                    :beginning-date-time "2002-01-01T12:00:00Z"
                                                    :ending-date-time "2002-01-31T12:00:00Z"}))
        coll10 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset10"
                                                     :beginning-date-time "2002-01-01T12:00:00Z"
                                                     :ending-date-time "2002-02-14T12:00:00Z"}))
        coll11 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset11"
                                                     :beginning-date-time "2002-03-14T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll12 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset12"
                                                     :beginning-date-time "2002-03-15T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll13 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset13"
                                                     :beginning-date-time "2002-04-01T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll14 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset14"
                                                     :beginning-date-time "1999-02-15T12:00:00Z"
                                                     :ending-date-time "1999-03-15T12:00:00Z"}))
        coll15 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset15"
                                                     :beginning-date-time "2003-02-15T12:00:00Z"
                                                     :ending-date-time "2003-03-15T12:00:00Z"}))
        coll16 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset16"
                                                     :beginning-date-time "1999-02-15T12:00:00Z"}))
        coll17 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset17"
                                                     :beginning-date-time "2001-02-15T12:00:00Z"}))
        coll18 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset18"
                                                     :beginning-date-time "2002-03-15T12:00:00Z"}))
        coll19 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset19"
                                                     :beginning-date-time "2001-11-15T12:00:00Z"
                                                     :ending-date-time "2001-12-15T12:00:00Z"}))
        coll20 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset20"}))]
    (index/flush-elastic-index)

    (testing "search by both start-day and end-day."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
                                          :page_size 100})]
        (is (d/refs-match? [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17] references))))
    (testing "search by end-day."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, , 90"
                                          :page_size 100})]
        (is (d/refs-match?
              [coll2 coll3 coll5 coll6 coll7 coll9 coll10 coll11 coll16 coll17] references))))
    (testing "search by start-day."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32,"
                                          :page_size 100})]
        (is (d/refs-match?
              [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll16 coll17 coll19] references))))
    (testing "search by start-day without end_date."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["2000-02-15T00:00:00Z, , 32"]
                                          :page_size 100})]
        (is (d/refs-match?
              [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll12 coll13 coll15 coll16 coll17 coll18 coll19]
              references))))
    (testing "search by start-day/end-day with date crossing year boundary."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["2000-04-03T00:00:00Z, 2002-01-02T00:00:00Z, 93, 2"]
                                          :page_size 100})]
        (is (d/refs-match?
              [coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll16 coll17 coll19] references))))
    (testing "search by multiple temporal."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["1998-01-15T00:00:00Z, 1999-03-15T00:00:00Z, 60, 90"
                                                        "2000-02-15T00:00:00Z, 2001-03-15T00:00:00Z, 40, 50"]
                                          :page_size 100})]
        (is (d/refs-match? [coll2 coll6 coll14 coll16 coll17] references))))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search with temporal_start_day and no temporal_start is invalid."
    (try
      (search/find-refs :collection {"temporal[]" ", , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_start_day must be accompanied by a temporal_start" body))))))
  (testing "search with temporal_end_day and no temporal_end is invalid."
    (try
      (search/find-refs :collection {"temporal[]" ", , , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_end_day must be accompanied by a temporal_end" body)))))))
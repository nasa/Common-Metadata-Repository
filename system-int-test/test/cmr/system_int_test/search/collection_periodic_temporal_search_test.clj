(ns ^{:doc "Integration test for CMR collection periodic temporal search"}
  cmr.system-int-test.search.collection-periodic-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-periodic-temporal
  (let [coll1 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2000-01-01T12:00:00Z"
                                                    :ending-date-time "2000-02-14T12:00:00Z"}))
        coll2 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2000-02-14T12:00:00Z"
                                                    :ending-date-time "2000-02-15T12:00:00Z"}))
        coll3 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2000-03-15T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        coll4 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2000-04-01T12:00:00Z"
                                                    :ending-date-time "2000-04-15T12:00:00Z"}))
        coll5 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-01-31T12:00:00Z"}))
        coll6 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2001-01-01T12:00:00Z"
                                                    :ending-date-time "2001-02-14T12:00:00Z"}))
        coll7 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2001-03-15T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        coll8 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2001-04-01T12:00:00Z"
                                                    :ending-date-time "2001-04-15T12:00:00Z"}))
        coll9 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2002-01-01T12:00:00Z"
                                                    :ending-date-time "2002-01-31T12:00:00Z"}))
        coll10 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2002-01-01T12:00:00Z"
                                                     :ending-date-time "2002-02-14T12:00:00Z"}))
        coll11 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2002-03-14T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll12 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2002-03-15T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll13 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2002-04-01T12:00:00Z"
                                                     :ending-date-time "2002-04-15T12:00:00Z"}))
        coll14 (d/ingest "PROV1" (dc/collection {:beginning-date-time "1999-02-15T12:00:00Z"
                                                     :ending-date-time "1999-03-15T12:00:00Z"}))
        coll15 (d/ingest "PROV1" (dc/collection {:beginning-date-time "2003-02-15T12:00:00Z"
                                                     :ending-date-time "2003-03-15T12:00:00Z"}))
        coll16 (d/ingest "PROV2" (dc/collection {:beginning-date-time "1999-02-15T12:00:00Z"}))
        coll17 (d/ingest "PROV2" (dc/collection {:beginning-date-time "2001-02-15T12:00:00Z"}))
        coll18 (d/ingest "PROV2" (dc/collection {:beginning-date-time "2002-03-15T12:00:00Z"}))
        coll19 (d/ingest "PROV2" (dc/collection {:beginning-date-time "2001-11-15T12:00:00Z"
                                                     :ending-date-time "2001-12-15T12:00:00Z"}))
        coll20 (d/ingest "PROV2" (dc/collection {}))]
    (index/wait-until-indexed)

    (testing "search by both start-day and end-day."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
                                          :page_size 100})]
        (is (d/refs-match? [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17] references))))
    (testing "search by both start-day and end-day - testing singular temporal."
      (let [extent "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
            references (search/find-refs :collection {"temporal[]" extent :page_size 100})
            references1 (search/find-refs :collection {"temporal" extent :page_size 100})]
        (is (= (dissoc references :took)
               (dissoc references1 :took)))))
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
        (is (d/refs-match? [coll2 coll6 coll14 coll16 coll17] references))))

    (testing "search by temporal with aql"
      (are [items start-date stop-date start-day end-day]
           (d/refs-match? items (search/find-refs-with-aql :collection [{:temporal {:start-date start-date
                                                                                    :stop-date stop-date
                                                                                    :start-day start-day
                                                                                    :end-day end-day}}]))

           ;; search by both start-day and end-day
           [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
           "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 90

           ;; search by start-day
           [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll16 coll17 coll19]
           "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 nil

           ;;search by start-day without end_date
           [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll12 coll13 coll15 coll16 coll17 coll18 coll19]
           "2000-02-15T00:00:00Z" nil 32 nil

           ;; search by start-day/end-day with date crossing year boundary
           [coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll16 coll17 coll19]
           "2000-04-03T00:00:00Z" "2002-01-02T00:00:00Z" 93 2))))


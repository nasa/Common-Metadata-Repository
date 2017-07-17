(ns cmr.system-int-test.search.granule-periodic-temporal-search-test
  "Integration test for CMR granule periodic temporal search"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-periodic-temporal
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:TemporalExtents
                                                                            [(data-umm-cmn/temporal-extent
                                                                              {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                   :beginning-date-time "2000-01-01T12:00:00Z"
                                                   :ending-date-time "2000-02-14T12:00:00Z"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule2"
                                                   :beginning-date-time "2000-02-14T12:00:00Z"
                                                   :ending-date-time "2000-02-15T12:00:00Z"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule3"
                                                   :beginning-date-time "2000-03-15T12:00:00Z"
                                                   :ending-date-time "2000-04-15T12:00:00Z"}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule4"
                                                   :beginning-date-time "2000-04-01T12:00:00Z"
                                                   :ending-date-time "2000-04-15T12:00:00Z"}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule5"
                                                   :beginning-date-time "2001-01-01T12:00:00Z"
                                                   :ending-date-time "2001-01-31T12:00:00Z"}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule6"
                                                   :beginning-date-time "2001-01-01T12:00:00Z"
                                                   :ending-date-time "2001-02-14T12:00:00Z"}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule7"
                                                   :beginning-date-time "2001-03-15T12:00:00Z"
                                                   :ending-date-time "2001-04-15T12:00:00Z"}))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule8"
                                                   :beginning-date-time "2001-04-01T12:00:00Z"
                                                   :ending-date-time "2001-04-15T12:00:00Z"}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule9"
                                                   :beginning-date-time "2002-01-01T12:00:00Z"
                                                   :ending-date-time "2002-01-31T12:00:00Z"}))
        gran10 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule10"
                                                    :beginning-date-time "2002-01-01T12:00:00Z"
                                                    :ending-date-time "2002-02-14T12:00:00Z"}))
        gran11 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule11"
                                                    :beginning-date-time "2002-03-14T12:00:00Z"
                                                    :ending-date-time "2002-04-15T12:00:00Z"}))
        gran12 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule12"
                                                    :beginning-date-time "2002-03-15T12:00:00Z"
                                                    :ending-date-time "2002-04-15T12:00:00Z"}))
        gran13 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule13"
                                                    :beginning-date-time "2002-04-01T12:00:00Z"
                                                    :ending-date-time "2002-04-15T12:00:00Z"}))
        gran14 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule14"
                                                    :beginning-date-time "1999-02-15T12:00:00Z"
                                                    :ending-date-time "1999-03-15T12:00:00Z"}))
        gran15 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule15"
                                                    :beginning-date-time "2003-02-15T12:00:00Z"
                                                    :ending-date-time "2003-03-15T12:00:00Z"}))
        gran16 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule16"
                                                    :beginning-date-time "1999-02-15T12:00:00Z"}))
        gran17 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule17"
                                                    :beginning-date-time "2001-02-15T12:00:00Z"}))
        gran18 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule18"
                                                    :beginning-date-time "2002-03-15T12:00:00Z"}))
        gran19 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule19"
                                                    :beginning-date-time "2001-11-15T12:00:00Z"
                                                    :ending-date-time "2001-12-15T12:00:00Z"}))
        gran20 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule20"}))]
    (index/wait-until-indexed)

    (testing "Search granules with periodic temporal parameter"
      (are2 [grans temporal-params]
            (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                             :page_size 100}))

            "search by both start-day and end-day"
            [gran2 gran3 gran6 gran7 gran10 gran11 gran16 gran17]
            "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"

            "search by start-day"
            [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran16 gran17 gran19]
            "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32,"

            "search by end-day"
            [gran2 gran3 gran5 gran6 gran7 gran9 gran10 gran11 gran16 gran17]
            "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, , 90"

            "search by start-day without end_date"
            [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran12 gran13 gran15 gran16 gran17 gran18 gran19]
            "2000-02-15T00:00:00Z, , 32"

            "search by start-day/end-day with date crossing year boundary"
            [gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10 gran16 gran17 gran19]
            "2000-04-03T00:00:00Z, 2002-01-02T00:00:00Z, 93, 2"

            "search by multiple temporal"
            [gran2 gran6 gran14 gran16 gran17]
            ["1998-01-15T00:00:00Z, 1999-03-15T00:00:00Z, 60, 90"
             "2000-02-15T00:00:00Z, 2001-03-15T00:00:00Z, 40, 50"]))

    (testing "search by both start-day and end-day - testing singular temporal."
      (let [extent "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
            references (search/find-refs :granule {"temporal[]" extent :page_size 100})
            references1 (search/find-refs :granule {"temporal" extent :page_size 100})]
        (is (= (dissoc references :took)
               (dissoc references1 :took)))))

    (testing "search granules by periodic temporal with aql"
      (are [items start-date stop-date start-day end-day]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule [{:temporal {:start-date start-date
                                                                           :stop-date stop-date
                                                                           :start-day start-day
                                                                           :end-day end-day}}]))

           ;; search by both start-day and end-day
           [gran2 gran3 gran6 gran7 gran10 gran11 gran16 gran17]
           "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 90

           ;; search by start-day
           [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran16 gran17 gran19]
           "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 nil

           ;;search by start-day without end_date
           [gran2 gran3 gran4 gran6 gran7 gran8 gran10 gran11 gran12 gran13 gran15 gran16 gran17 gran18 gran19]
           "2000-02-15T00:00:00Z" nil 32 nil

           ;; search by start-day/end-day with date crossing year boundary
           [gran3 gran4 gran5 gran6 gran7 gran8 gran9 gran10 gran16 gran17 gran19]
           "2000-04-03T00:00:00Z" "2002-01-02T00:00:00Z" 93 2))))

(deftest search-by-periodic-temporal-boundary-conditions
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:TemporalExtents
                                                                            [(data-umm-cmn/temporal-extent
                                                                              {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                   :beginning-date-time "2012-01-01T00:00:00Z"
                                                   :ending-date-time "2012-01-02T00:00:00Z"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule2"
                                                   :beginning-date-time "2012-02-14T00:00:00Z"
                                                   :ending-date-time "2012-02-18T00:00:00Z"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule3"
                                                   :beginning-date-time "2012-04-08T00:00:00Z"
                                                   :ending-date-time "2012-04-09T00:00:00Z"}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule4"
                                                   :beginning-date-time "2012-04-21T00:00:00Z"
                                                   :ending-date-time "2012-04-22T00:00:00Z"}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule5"
                                                   :beginning-date-time "2012-06-01T00:00:00Z"
                                                   :ending-date-time "2012-06-02T00:00:00Z"}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule6"
                                                   :beginning-date-time "2012-12-21T00:00:00Z"
                                                   :ending-date-time "2012-12-22T00:00:00Z"}))]
    (index/wait-until-indexed)

    (testing "periodic temporal on the start-year"
      (testing "start-day before end-day"
        (are2 [grans temporal-params]
              (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                               :page_size 100}))

              "start-date before start-day"
              [gran3 gran4]
              "2012-02-01T00:00:00Z, 2015-02-01T00:00:00Z, 90, 120"

              "start-date between start-day and end-day"
              [gran4]
              "2012-04-15T00:00:00Z, 2015-02-01T00:00:00Z, 90, 120"

              "start-date after end-day"
              []
              "2012-06-15T00:00:00Z, 2015-02-01T00:00:00Z, 90, 120"))

      (testing "start-day after end-day"
        (are2 [grans temporal-params]
              (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                               :page_size 100}))

              "start-date before end-day"
              [gran5 gran6]
              "2012-02-01T00:00:00Z, 2015-02-01T00:00:00Z, 120, 90"

              "start-date between start-day and end-day"
              [gran5 gran6]
              "2012-04-15T00:00:00Z, 2015-02-01T00:00:00Z, 120, 90"

              "start-date after start-day"
              [gran6]
              "2012-06-15T00:00:00Z, 2015-02-01T00:00:00Z, 120, 90")))

    (testing "periodic temporal on the end-year"
      (testing "start-day before end-day"
        (are2 [grans temporal-params]
              (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                               :page_size 100}))

              "end-date before start-day"
              []
              "2000-02-01T00:00:00Z, 2012-02-01T00:00:00Z, 90, 120"

              "end-date between start-day and end-day"
              [gran3]
              "2000-02-01T00:00:00Z, 2012-04-15T00:00:00Z, 90, 120"

              "end-date after end-day"
              [gran3 gran4]
              "2000-02-01T00:00:00Z, 2012-06-15T00:00:00Z, 90, 120"))

      (testing "start-day after end-day"
        (are2 [grans temporal-params]
              (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                               :page_size 100}))

              "end-date before end-day"
              [gran1]
              "2000-02-01T00:00:00Z, 2012-02-01T00:00:00Z, 120, 90"

              "end-date between start-day and end-day"
              [gran1 gran2]
              "2000-02-01T00:00:00Z, 2012-04-15T00:00:00Z, 120, 90"

              "end-date after start-day"
              [gran1 gran2 gran5]
              "2000-02-01T00:00:00Z, 2012-06-15T00:00:00Z, 120, 90")))

    (testing "perodic temporal search on a single day"
      (are2 [grans temporal-params]
            (d/refs-match? grans (search/find-refs :granule {"temporal[]" temporal-params
                                                             :page_size 100}))

            "match granule on beginning-datetime"
            [gran2]
            "2000-02-01T00:00:00Z, 2015-02-01T00:00:00Z, 45, 45"

            "match granule contains the day"
            [gran2]
            "2000-02-01T00:00:00Z, 2012-04-15T00:00:00Z, 48, 48"

            "match granule on end-datetime"
            [gran2]
            "2000-02-01T00:00:00Z, 2012-04-15T00:00:00Z, 49, 49"

            "search by rolling temporal with end-date not intersect the day interval.
            This is a limitation of rolling temporal parameter search where searching on the end year
            where end-date not intersect start-day and end-day will not find anything in that year."
            []
            "2000-02-01T00:00:00Z, 2012-01-15T00:00:00Z, 48, 48"))))

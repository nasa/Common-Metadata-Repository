(ns cmr.system-int-test.search.collection-periodic-temporal-search-test
  "Integration test for CMR collection periodic temporal search"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :refer [are2]]
    [cmr.system-int-test.data2.core :as data-core]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-periodic-temporal
  (let [coll1 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 1 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2000-01-01T12:00:00Z"
                                            :ending-date-time "2000-02-14T12:00:00Z"})]}))
        coll2 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 2 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2000-02-14T12:00:00Z"
                                            :ending-date-time "2000-02-15T12:00:00Z"})]}))
        coll3 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 3 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2000-03-15T12:00:00Z"
                                            :ending-date-time "2000-04-15T12:00:00Z"})]}))
        coll4 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 4 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2000-04-01T12:00:00Z"
                                            :ending-date-time "2000-04-15T12:00:00Z"})]}))
        coll5 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 5 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2001-01-01T12:00:00Z"
                                            :ending-date-time "2001-01-31T12:00:00Z"})]}))
        coll6 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 6 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2001-01-01T12:00:00Z"
                                            :ending-date-time "2001-02-14T12:00:00Z"})]}))
        coll7 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 7 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2001-03-15T12:00:00Z"
                                            :ending-date-time "2001-04-15T12:00:00Z"})]}))
        coll8 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 8 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2001-04-01T12:00:00Z"
                                            :ending-date-time "2001-04-15T12:00:00Z"})]}))
        coll9 (data-core/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection 9 {:TemporalExtents
                                         [(data-umm-cmn/temporal-extent
                                           {:beginning-date-time "2002-01-01T12:00:00Z"
                                            :ending-date-time "2002-01-31T12:00:00Z"})]}))
        coll10 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 10 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2002-01-01T12:00:00Z"
                                              :ending-date-time "2002-02-14T12:00:00Z"})]}))
        coll11 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 11 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2002-03-14T12:00:00Z"
                                              :ending-date-time "2002-04-15T12:00:00Z"})]}))
        coll12 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 12 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2002-03-15T12:00:00Z"
                                              :ending-date-time "2002-04-15T12:00:00Z"})]}))
        coll13 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 13 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2002-04-01T12:00:00Z"
                                              :ending-date-time "2002-04-15T12:00:00Z"})]}))
        coll14 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 14 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "1999-02-15T12:00:00Z"
                                              :ending-date-time "1999-03-15T12:00:00Z"})]}))
        coll15 (data-core/ingest-umm-spec-collection
                "PROV1"
                (data-umm-c/collection 15 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2003-02-15T12:00:00Z"
                                              :ending-date-time "2003-03-15T12:00:00Z"})]}))
        coll16 (data-core/ingest-umm-spec-collection
                "PROV2"
                (data-umm-c/collection 16 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "1999-02-15T12:00:00Z"})]}))
        coll17 (data-core/ingest-umm-spec-collection
                "PROV2"
                (data-umm-c/collection 17 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2001-02-15T12:00:00Z"})]}))
        coll18 (data-core/ingest-umm-spec-collection
                "PROV2"
                (data-umm-c/collection 18 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2002-03-15T12:00:00Z"})]}))
        coll19 (data-core/ingest-umm-spec-collection
                "PROV2"
                (data-umm-c/collection 19 {:TemporalExtents
                                           [(data-umm-cmn/temporal-extent
                                             {:beginning-date-time "2001-11-15T12:00:00Z"
                                              :ending-date-time "2001-12-15T12:00:00Z"})]}))]
    (index/wait-until-indexed)

    (testing "search by both start-day and end-day - testing singular temporal."
      (let [extent "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"
            references (search/find-refs :collection {"temporal[]" extent :page_size 100})
            references1 (search/find-refs :collection {"temporal" extent :page_size 100})]
        (is (= (dissoc references :took)
               (dissoc references1 :took)))))

    (testing "search by temporal params"
      (are2 [items search]
            (data-core/refs-match? items (search/find-refs :collection (assoc search :page_size 100)))

            "search by both start-day and end-day"
            [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
            {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"}

            "search by end-day"
            [coll2 coll3 coll5 coll6 coll7 coll9 coll10 coll11 coll16 coll17]
            {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, , 90"}

            "search by start-day"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll16 coll17 coll19]
            {"temporal[]" "2000-02-15T00:00:00Z/P2Y1M, 32,"}

            "search by start-day without end_date"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll12 coll13 coll15 coll16 coll17 coll18 coll19]
            {"temporal[]" ["2000-02-15T00:00:00Z, , 32"]}

            "search by start-day/end-day with date crossing year boundary"
            [coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll16 coll17 coll19]
            {"temporal[]" ["2000-04-03T00:00:00Z, 2002-01-02T00:00:00Z, 93, 2"]}

            "search without start-date"
            [coll1 coll2 coll14 coll16]
            {"temporal[]" ", 2000-02-15T00:00:00Z, 32, 90"}

            "search by multiple temporal"
            [coll2 coll6 coll14 coll16 coll17]
            {"temporal[]"["1998-01-15T00:00:00Z, 1999-03-15T00:00:00Z, 60, 90"
                          "2000-02-15T00:00:00Z, 2001-03-15T00:00:00Z, 40, 50"]}))

    (testing "search by temporal with aql"
      (are2 [items start-date stop-date start-day end-day]
            (data-core/refs-match?
             items
             (search/find-refs-with-aql :collection [{:temporal {:start-date start-date
                                                                 :stop-date stop-date
                                                                 :start-day start-day
                                                                 :end-day end-day}}]))
            "search by both start-day and end-day"
            [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
            "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 90

            "search by start-day"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll16 coll17 coll19]
            "2000-02-15T00:00:00Z" "2002-03-15T00:00:00Z" 32 nil

            "search by start-day without end_date"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll12 coll13 coll15 coll16 coll17 coll18 coll19]
            "2000-02-15T00:00:00Z" nil 32 nil

            "search by start-day/end-day with date crossing year boundary"
            [coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll16 coll17 coll19]
            "2000-04-03T00:00:00Z" "2002-01-02T00:00:00Z" 93 2))

    (testing "Search collections by temporal using a JSON Query"
      (are2 [items search]
            (data-core/refs-match? items (search/find-refs-with-json-query :collection {:page_size 100} search))

            "search by both start-day and end-day"
            [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :end_date "2002-03-15T00:00:00Z"
                        :recurring_start_day 32
                        :recurring_end_day 90
                        :exclude_boundary false}}

            "search by end-day"
            [coll2 coll3 coll5 coll6 coll7 coll9 coll10 coll11 coll16 coll17]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :end_date "2002-03-15T00:00:00Z"
                        :recurring_end_day 90}}

            "search by start-day"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll16 coll17 coll19]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :end_date "2002-03-15T00:00:00Z"
                        :recurring_start_day 32}}

            "search by start-day without end_date"
            [coll2 coll3 coll4 coll6 coll7 coll8 coll10 coll11 coll12 coll13 coll15 coll16 coll17 coll18 coll19]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :recurring_start_day 32}}

            "search by start-day/end-day with date crossing year boundary"
            [coll3 coll4 coll5 coll6 coll7 coll8 coll9 coll10 coll16 coll17 coll19]
            {:temporal {:start_date "2000-04-03T00:00:00Z"
                        :end_date "2002-01-02T00:00:00Z"
                        :recurring_start_day 93
                        :recurring_end_day 2}}

            "search with exclude_boundary true"
            [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :end_date "2002-03-15T00:00:00Z"
                        :recurring_start_day 32
                        :recurring_end_day 90
                        :exclude_boundary true}}

            "search with exclude_boundary false"
            [coll2 coll3 coll6 coll7 coll10 coll11 coll16 coll17]
            {:temporal {:start_date "2000-02-15T00:00:00Z"
                        :end_date "2002-03-15T00:00:00Z"
                        :recurring_start_day 32
                        :recurring_end_day 90
                        :exclude_boundary false}}

            "search without start-date"
            [coll1 coll2 coll14 coll16]
            {:temporal {:end_date "2000-02-15T00:00:00Z"
                        :recurring_start_day 32
                        :recurring_end_day 90}}

            "search by multiple temporal"
            [coll2 coll6 coll14 coll16 coll17]
            {:or [{:temporal {:start_date "1998-01-15T00:00:00Z"
                              :end_date "1999-03-15T00:00:00Z"
                              :recurring_start_day 60
                              :recurring_end_day 90}}
                  {:temporal {:start_date "2000-02-15T00:00:00Z"
                              :end_date "2001-03-15T00:00:00Z"
                              :recurring_start_day 40
                              :recurring_end_day 50}}]}))))

(deftest search-period-temporal-json-error-scenarios
  (testing "search by invalid temporal day format"
    (are2 [search error-field field-type expected-type]
          (let [{:keys [status errors]} (search/find-refs-with-json-query :collection {} search)
                expected-error (format "#/condition/temporal/%s: expected type: %s, found: %s"
                                       (name error-field)
                                       expected-type
                                       field-type)]
            (= [400 [expected-error]]
               [status errors]))

          "invalid recurring_start_day, type string"
          {:temporal {:recurring_start_day "40"}}
          :recurring_start_day "String" "Integer"

          "invalid recurring_start_day, type number"
          {:temporal {:recurring_start_day 4.0}}
          :recurring_start_day "BigDecimal" "Integer"

          "invalid recurring_end_day, type string"
          {:temporal {:recurring_start_day  5 :recurring_end_day "40"}}
          :recurring_end_day "String" "Integer"

          "invalid recurring_end_day, type number"
          {:temporal {:recurring_start_day  5 :recurring_end_day 4.0}}
          :recurring_end_day "BigDecimal" "Integer")))

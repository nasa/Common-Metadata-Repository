(ns cmr.system-int-test.search.collection-temporal-search-test
  "Integration test for CMR collection temporal search"
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common.util :refer [are2]]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.dev-system-util :as dev-system]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "CMR_T_PROV"}))


;; This tests searching with the limit-to-granules options. That finds collections by the temporal
;; values of the min and max granule of the collection or the collection's temporal values if the
;; collection has no granules
(deftest search-by-temporal-limit-to-granules
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "coll1"
                                                :beginning-date-time "2000-01-01T00:00:00Z"
                                                :ending-date-time "2010-01-01T00:00:00Z"}))
        c1-g1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "c1-g1"
                                                   :beginning-date-time "2003-01-01T00:00:00Z"
                                                   :ending-date-time "2004-01-01T00:00:00Z"}))
        c1-g2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "c1-g2"
                                                   :beginning-date-time "2005-01-01T00:00:00Z"
                                                   :ending-date-time "2006-01-01T00:00:00Z"}))
        c1-g3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "c1-g3"
                                                   :beginning-date-time "2007-01-01T00:00:00Z"
                                                   :ending-date-time "2008-01-01T00:00:00Z"}))

        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "coll2"
                                                :beginning-date-time "2001-01-01T00:00:00Z"
                                                :ending-date-time "2005-01-01T00:00:00Z"}))
        c2-g1 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "c2-g1"
                                                   :beginning-date-time "2001-01-01T00:00:00Z"
                                                   :ending-date-time "2002-01-01T00:00:00Z"}))
        c2-g2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "c2-g2"
                                                   :beginning-date-time "2003-01-01T00:00:00Z"
                                                   :ending-date-time "2004-01-01T00:00:00Z"}))

        ;; Collection 3 has no granules
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "coll3"
                                                :beginning-date-time "2002-01-01T00:00:00Z"
                                                :ending-date-time "2005-01-01T00:00:00Z"}))

        ;; Collection 4 has no granules and no end date.
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "coll4"
                                                :beginning-date-time "2004-01-01T00:00:00Z"}))

        ;; Collection 5 has granules and no end date.
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "coll5"
                                                :beginning-date-time "2004-01-01T00:00:00Z"}))
        c5-g1 (d/ingest "PROV1" (dg/granule coll5 {:granule-ur "c5-g1"
                                                   :beginning-date-time "2006-01-01T00:00:00Z"
                                                   :ending-date-time "2007-01-01T00:00:00Z"}))
        c5-g2 (d/ingest "PROV1" (dg/granule coll5 {:granule-ur "c5-g2"
                                                   :beginning-date-time "2008-01-01T00:00:00Z"}))

        ;; Coll6 is an NRT collection with granules that have temporal in recent time
        coll6 (d/ingest "PROV1" (dc/collection {:entry-title "coll6"
                                                :beginning-date-time "2006-01-01T00:00:00Z"}))
        dt-2-days-ago (f/unparse (f/formatters :date-time) (t/minus (t/now) (t/days 2)))
        dt-1-day-ago (f/unparse (f/formatters :date-time) (t/minus (t/now) (t/days 1)))
        c6-g1 (d/ingest "PROV1" (dg/granule coll6 {:granule-ur "c6-g1"
                                                   :beginning-date-time "2009-01-01T00:00:00Z"
                                                   :ending-date-time dt-2-days-ago}))]
    (index/wait-until-indexed)

    ;; Refresh the aggregate cache so that it includes all the granules that were added.
    (index/refresh-collection-granule-aggregate-cache)
    ;; Reindex all the collections to get the latest information.
    (ingest/reindex-all-collections)
    (index/wait-until-indexed)

    (are2 [items search]
      (is (d/refs-match? items (search/find-refs :collection search)))

      "All are found"
      [coll1 coll2 coll3 coll4 coll5 coll6] {"temporal[]" "2000-01-01T00:00:00Z,2010-01-01T00:00:00Z"
                                             "options[temporal][limit_to_granules]" true}

      "coll1 is not found because it has no granules before this point"
      [coll2 coll3] {"temporal[]" "2001-01-01T00:00:00Z,2002-01-01T00:00:00Z"
                     "options[temporal][limit_to_granules]" true}

      "Search by temporal start and end day is supported"
      [coll2 coll3] {"temporal[]" "2000-01-01T00:00:00Z,2003-01-01T00:00:00Z,50,70"
                     "options[temporal][limit_to_granules]" true}

      "coll1 is found when not limited to granules"
      [coll1 coll2 coll3] {"temporal[]" "2001-01-01T00:00:00Z,2002-01-01T00:00:00Z"}


      "coll2 is not found because it has no granules after this point"
      [coll1 coll3 coll4 coll5 coll6] {"temporal[]" "2004-06-01T00:00:00Z,"
                                       "options[temporal][limit_to_granules]" true}

      "coll2 is found when not limited to granules"
      [coll1 coll2 coll3 coll4 coll5 coll6] {"temporal[]" "2004-06-01T00:00:00Z,"}


      "coll5 is not found because it has no granules before 2006"
      [coll1 coll3 coll4] {"temporal[]" "2004-06-01T00:00:00Z,2005-01-01T00:00:00Z"
                           "options[temporal][limit_to_granules]" true}

      "Collections with no end date are found"
      [coll4 coll5 coll6] {"temporal[]" "2011-01-01T00:00:00Z,2012-01-01T00:00:00Z"
                           "options[temporal][limit_to_granules]" true}

      "An NRT collection with granules that have temporal in the past 3 days will be found when searching with later dates"
      ;; coll6 is found because it has a granule with a temporal ending within 2 days ago.
      ;; This is a special rule for collections with recent data.
      [coll4 coll5 coll6] {"temporal[]" (str dt-1-day-ago ",")
                           "options[temporal][limit_to_granules]" true})))


(deftest search-by-temporal
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "2010-01-01T12:00:00Z"
                                                :ending-date-time "2010-01-11T12:00:00Z"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :beginning-date-time "2010-01-31T12:00:00Z"
                                                :ending-date-time "2010-12-12T12:00:00Z"}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset3"
                                                :beginning-date-time "2010-12-03T12:00:00Z"
                                                :ending-date-time "2010-12-20T12:00:00Z"}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset4"
                                                :beginning-date-time "2010-12-12T12:00:00Z"
                                                :ending-date-time "2011-01-03T12:00:00Z"}))
        coll5 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset5"
                                                :beginning-date-time "2011-02-01T12:00:00Z"
                                                :ending-date-time "2011-03-01T12:00:00Z"}))
        coll6 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset6"
                                                :beginning-date-time "2010-01-30T12:00:00Z"}))
        coll7 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset7"
                                                :beginning-date-time "2010-12-12T12:00:00Z"}))
        coll8 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset8"
                                                :beginning-date-time "2011-12-13T12:00:00Z"}))
        coll9 (d/ingest "PROV2" (dc/collection {:entry-title "Dataset9"}))
        coll10 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset10"
                                                 :single-date-time "2010-05-01T00:00:00Z"}))
        coll11 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset11"
                                                 :single-date-time "1999-05-01T00:00:00Z"}))

        ;; Collection 12 is way in the past and has an ends at present flag set to false
        coll12 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset12"
                                                 :beginning-date-time "1965-12-12T12:00:00Z"
                                                 :ending-date-time "1966-01-03T12:00:00Z"
                                                 :ends-at-present? false}))

        ;; Collection 13 is way in the past and has an ends at present flag set to true
        coll13 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset13"
                                                 :beginning-date-time "1965-12-12T12:00:00Z"
                                                 :ending-date-time "1966-01-03T12:00:00Z"
                                                 :ends-at-present? true}))]
    (index/wait-until-indexed)

    (testing "search by temporal params"
      (are2 [items search]
        (d/refs-match? items (search/find-refs :collection search))

        "search by temporal_start"
        [coll2 coll3 coll4 coll5 coll6 coll7 coll8 coll13] {"temporal[]" "2010-12-12T12:00:00Z,"}

        "search by temporal_end"
        [coll1 coll2 coll3 coll4 coll6 coll7 coll10 coll11 coll12 coll13] {"temporal[]" "/2010-12-12T12:00:00Z"}

        "search by temporal_range"
        [coll1 coll13] {"temporal[]" "2010-01-01T10:00:00Z, 2010-01-10T12:00:00Z"}

        "search by temporal_range.options: :exclude-boundary false"
        [coll1 coll6 coll13] {"temporal[]" "2010-01-11T12:00:00Z, 2010-01-30T12:00:00Z"
                              "options[temporal][exclude_boundary]" "false"}

        "search by temporal_range.options: :exclude-boundary true"
        [coll13] {"temporal[]" "2010-01-11T12:00:00Z, 2010-01-30T12:00:00Z"
                  "options[temporal][exclude_boundary]" "true"}

        "search by multiple temporal_range"
        [coll1 coll2 coll6 coll13] {"temporal[]" ["2010-01-01T10:00:00Z, 2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z/2010-02-22T10:00:00Z"]}

        "search by multiple temporal_range, options :or"
        [coll1 coll2 coll6 coll13] {"temporal[]" ["2010-01-01T10:00:00Z, 2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z/P1Y"]
                                    "options[temporal][or]" ""}

        "search by multiple temporal_range, options :and"
        [coll1 coll13] {"temporal[]" ["2010-01-01T10:00:00Z, 2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                        "options[temporal][and]" "true"}

        "search by multiple temporal_range, options :or :exclude-boundary"
        [coll2 coll6 coll10 coll13] {"temporal[]" ["2009-02-22T10:00:00Z,2010-01-01T12:00:00Z" "2010-01-11T12:00:00Z, 2010-12-03T12:00:00Z"]
                                     "options[temporal][or]" "true"
                                     "options[temporal][exclude_boundary]" "true"}

        "search by temporal_range - iso8601 interval"
        [coll1 coll13] {"temporal[]" "2010-01-01T10:00:00Z/P10DT2H"}))

    (testing "search by temporal date-range with aql"
      (are2 [items start-date stop-date]
        (d/refs-match? items (search/find-refs-with-aql :collection [{:temporal {:start-date start-date
                                                                                 :stop-date stop-date}}]))

        "search by temporal_start"
        [coll2 coll3 coll4 coll5 coll6 coll7 coll8 coll13] "2010-12-12T12:00:00Z" nil

        "search by temporal_range intersect"
        [coll1 coll13] "2010-01-01T10:00:00Z" "2010-01-10T12:00:00Z"

        "search by temporal_range intersect/contain"
        [coll2 coll6 coll10 coll13] "2010-04-01T10:00:00Z" "2010-06-10T12:00:00Z"))

    (testing "Search collections by temporal using a JSON Query"
      (are2 [items search]
        (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

        "search by range"
        [coll1 coll13] {:temporal {:start_date "2010-01-01T10:00:00Z"
                                   :end_date "2010-01-10T12:00:00Z"}}

        "search by start date"
        [coll2 coll3 coll4 coll5 coll6 coll7 coll8 coll13]
        {:temporal {:start_date "2010-12-12T12:00:00Z"}}

        "search by end date"
        [coll1 coll2 coll3 coll4 coll6 coll7 coll10 coll11 coll12 coll13]
        {:temporal {:end_date "2010-12-12T12:00:00Z"}}

        "search by not temporal"
        [coll5 coll8 coll9] {:not {:temporal {:end_date "2010-12-12T12:00:00Z"}}}

        "search by range :exclude-boundary false"
        [coll1 coll6 coll13] {:temporal {:start_date "2010-01-01T10:00:00Z"
                                         :end_date "2010-01-30T12:00:00Z"
                                         :exclude_boundary false}}

        "search by range :exclude-boundary true"
        [coll13] {:temporal {:start_date "2010-01-11T12:00:00Z"
                             :end_date "2010-01-30T12:00:00Z"
                             :exclude_boundary true}}

        "search by multiple temporal_range, options :or"
        [coll1 coll2 coll6 coll13] {:or [{:temporal {:start_date "2010-01-01T10:00:00Z"
                                                     :end_date "2010-01-10T12:00:00Z"}}
                                         {:temporal {:start_date "2009-02-22T10:00:00Z"
                                                     :end_date "2010-02-22T10:00:00Z"}}]}

        "search by multiple temporal_range, options :and"
        [coll1 coll13] {:and [{:temporal {:start_date "2010-01-01T10:00:00Z"
                                          :end_date "2010-01-10T12:00:00Z"}}
                              {:temporal {:start_date "2009-02-22T10:00:00Z"
                                          :end_date "2010-02-22T10:00:00Z"}}]}

        "search by multiple temporal_range, options :or :exclude-boundary"
        [coll2 coll3 coll6 coll10 coll13] {:or [{:temporal {:start_date "2009-02-22T10:00:00Z"
                                                            :end_date "2010-01-01T12:00:00Z"
                                                            :exclude_boundary true}}
                                                {:temporal {:start_date "2010-01-12T12:00:00Z"
                                                            :end_date "2010-12-03T12:00:00Z"
                                                            :exclude_boundary false}}]}))))


;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-params-error-scenarios
  (testing "search by invalid temporal format."
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2010-13-12T12:00:00,"})]
      (is (= 400 status))
      (is (re-find #"temporal start datetime is invalid: \[2010-13-12T12:00:00\] is not a valid datetime" (first errors)))))
  (testing "periodic temporal search that produces empty search ranges."
    (let [{:keys [status errors]} (search/find-refs :collection
                            {"temporal[]" "2016-05-10T08:18:16Z,2016-05-10T08:34:31Z,131,131"})]
      (is (= 400 status))
      (is (re-find #"Periodic temporal search produced no searchable ranges and is invalid." (first errors)))))
  (testing "search by invalid temporal start-date after end-date."
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
      (is (= 400 status))
      (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" (first errors)))))
  (testing "search by invalid iso8601 temporal interval."
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2010-01-01T10:00:00Z/P10D2H"})]
      (is (= 422 status))
      (is (re-find #"\[2010-01-01T10:00:00Z/P10D2H\] is not a valid date-range : Invalid format: \"P10D2H\" is malformed at \"2H\"" (first errors)))))
  (testing "search by invalid optional parameters will return a valid error message"
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[][temporal_start]" "2010-01-01T10:00:00Z/P10D2H"})]
      (is (= 400 status))
      (is (re-find #"The valid format for temporal parameters are temporal\[\]=startdate,stopdate and temporal\[\]=startdate,stopdate,startday,endday" (first errors))))
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[temporal_start]" "2010-01-01T10:00:00Z/P10D2H"})]
      (is (= 400 status))
      (is (re-find #"The valid format for temporal parameters are temporal\[\]=startdate,stopdate and temporal\[\]=startdate,stopdate,startday,endday" (first errors))))
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2010-01-01T00:00:00Z,2011-01-10T00:00:00Z"
                                                                 "temporal[][temporal_start]" "2010-01-01T10:00:00Z/P10D2H"})]
      (is (= 400 status))
      (is (re-find #"Parameter \[temporal\] may be either single valued or multivalued, but not both\." (first errors))))))

(deftest search-temporal-json-error-scenarios
  (testing "search by invalid temporal date format"
    (are2 [search invalid-field-dates]
          (let [{:keys [status errors]} (search/find-refs-with-json-query :collection {} search)
                expected-errors (map #(apply format "/condition/temporal/%s string \"%s\" is invalid against requested date format(s) [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.SSSZ]" %)
                                     invalid-field-dates)]
            (= [400 expected-errors] [status errors]))

          "invalid start_date"
          {:temporal {:start_date "2010-13-12T12:00:00"}}
          {"start_date" "2010-13-12T12:00:00"}

          "invalid end_date"
          {:temporal {:end_date "2011-13-12T12:00:00"}}
          {"end_date" "2011-13-12T12:00:00"}


          "invalid start_date and end_date"
          {:temporal {:start_date "2010-13-12T12:00:00" :end_date "2011-13-12T12:00:00"}}
          {"end_date" "2011-13-12T12:00:00" "start_date" "2010-13-12T12:00:00"}))

  (testing "search by empty temporal"
    (let [{:keys [status errors]} (search/find-refs-with-json-query :collection {} {:temporal {}})]
      (is (= 400 status))
      (is (= "/condition/temporal object has too few properties (found 0 but schema requires at least 1)"
             (first errors)))))

  (testing "search by temporal with only exclude_boundary"
    (let [{:keys [status errors]} (search/find-refs-with-json-query
                                    :collection {} {:temporal {:exclude_boundary false}})]
      (is (= 400 status))
      (is (= "Temporal condition with only exclude_boundary is invalid."
             (first errors)))))

  (testing "search by invalid exclude_boundary format"
    (let [{:keys [status errors]} (search/find-refs-with-json-query
                                    :collection {} {:temporal {:exclude_boundary "false"}})]
      (is (= 400 status))
      (is (= "/condition/temporal/exclude_boundary instance type (string) does not match any allowed primitive type (allowed: [\"boolean\"])"
             (first errors))))))

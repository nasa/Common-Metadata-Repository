(ns cmr.system-int-test.search.granule-temporal-search-test
  "Integration test for CMR granule temporal search"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-temporal
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:TemporalExtents
                                                                            [(data-umm-cmn/temporal-extent
                                                                              {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule1"
                                                   :beginning-date-time "2010-01-01T12:00:00Z"
                                                   :ending-date-time "2010-01-11T12:00:00Z"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule2"
                                                   :beginning-date-time "2010-01-31T12:00:00Z"
                                                   :ending-date-time "2010-12-12T12:00:00Z"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule3"
                                                   :beginning-date-time "2010-12-03T12:00:00Z"
                                                   :ending-date-time "2010-12-20T12:00:00Z"}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule4"
                                                   :beginning-date-time "2010-12-12T12:00:00Z"
                                                   :ending-date-time "2011-01-03T12:00:00Z"}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule5"
                                                   :beginning-date-time "2011-02-01T12:00:00Z"
                                                   :ending-date-time "2011-03-01T12:00:00Z"}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule6"
                                                   :beginning-date-time "2010-01-30T12:00:00Z"}))
        gran7 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule7"
                                                   :beginning-date-time "2010-12-12T12:00:00Z"}))
        gran8 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule8"
                                                   :beginning-date-time "2011-12-13T12:00:00Z"}))
        gran9 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {:granule-ur "Granule9"}))]
    (index/wait-until-indexed)

    (testing "search by temporal_start."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2010-12-12T12:00:00Z,"})]
        (is (d/refs-match? [gran2 gran3 gran4 gran5 gran6 gran7 gran8] references))))
    (testing "search by temporal_end."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ",2010-12-12T12:00:00Z"})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4 gran6 gran7] references))))
    (testing "search by temporal_range."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2010-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by temporal_range, options :exclude-boundary false"
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2010-01-11T12:00:00Z,2010-01-31T12:00:00Z"
                                          "options[temporal][exclude_boundary]" "false"})]
        (is (d/refs-match? [gran1 gran2 gran6] references))))
    (testing "search by temporal_range, options :exclude-boundary true"
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2010-01-11T12:00:00Z,2010-01-31T12:00:00Z"
                                          "options[temporal][exclude_boundary]" "true"})]
        (is (d/refs-match? [gran6] references))))
    (testing "search by temporal_range with alternative datetime format."
      (let [references (search/find-refs :granule
                                         {"temporal[]" "2010-01-01T10:00:00,2010-01-10T12:00:00.123Z"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by multiple temporal_range."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]})]
        (is (d/refs-match? [gran1 gran2 gran6] references))))
    (testing "search by multiple temporal_range, options :and."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                                          "options[temporal][and]" "true"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by multiple temporal_range, options :or :exclude-boundary"
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2009-02-22T10:00:00Z,2010-01-31T12:00:00Z" "2010-12-20T12:00:00Z,2011-02-01T12:00:00Z"]
                                          "options[temporal][or]" "true"
                                          "options[temporal][exclude_boundary]" "true"})]
        (is (d/refs-match? [gran1 gran4 gran6 gran7] references))))

    (testing "search granules by temporal with aql"
      (are [items start-date stop-date]
           (d/refs-match? items (search/find-refs-with-aql :granule [{:temporal {:start-date start-date
                                                                                 :stop-date stop-date}}]))

           [gran2 gran3 gran4 gran5 gran6 gran7 gran8] "2010-12-12T12:00:00Z" nil
           [gran1] "2010-01-01T10:00:00Z" "2010-01-10T12:00:00Z"
           [gran1] "2010-01-01T10:00:00" "2010-01-10T12:00:00.123Z"))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search by invalid temporal format."
    (let [{:keys [status errors]} (search/find-refs :granule {"temporal[]" "2010-13-12T12:00:00,"})]
      (is (= 400 status))
      (is (re-find #"temporal start datetime is invalid: \[2010-13-12T12:00:00\] is not a valid datetime" (first errors)))))
  (testing "search by invalid temporal start-date after end-date."
    (let [{:keys [status errors]} (search/find-refs :granule {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
      (is (= 400 status))
      (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" (first errors))))))


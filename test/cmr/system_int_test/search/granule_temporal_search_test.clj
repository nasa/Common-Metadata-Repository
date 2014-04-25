(ns cmr.system-int-test.search.granule-temporal-search-test
  "Integration test for CMR granule temporal search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-by-temporal
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :beginning-date-time "2010-01-01T12:00:00Z"
                                                       :ending-date-time "2010-01-11T12:00:00Z"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                       :beginning-date-time "2010-01-31T12:00:00Z"
                                                       :ending-date-time "2010-12-12T12:00:00Z"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule3"
                                                       :beginning-date-time "2010-12-03T12:00:00Z"
                                                       :ending-date-time "2010-12-20T12:00:00Z"}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule4"
                                                       :beginning-date-time "2010-12-12T12:00:00Z"
                                                       :ending-date-time "2011-01-03T12:00:00Z"}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule5"
                                                       :beginning-date-time "2011-02-01T12:00:00Z"
                                                       :ending-date-time "2011-03-01T12:00:00Z"}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule6"
                                                       :beginning-date-time "2010-01-30T12:00:00Z"}))
        gran7 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule7"
                                                       :beginning-date-time "2010-12-12T12:00:00Z"}))
        gran8 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule8"
                                                       :beginning-date-time "2011-12-13T12:00:00Z"}))
        gran9 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule9"}))]
    (index/flush-elastic-index)

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
    (testing "search by multiple temporal_range."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]})]
        (is (d/refs-match? [gran1 gran2 gran6] references))))
    (testing "search by multiple temporal_range, options :or."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                                          "options[temporal][or]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran6] references))))
    (testing "search by multiple temporal_range, options :and."
      (let [references (search/find-refs :granule
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                                          "options[temporal][and]" "true"})]
        (is (d/refs-match? [gran1] references))))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search by invalid temporal format."
    (try
      (search/find-refs :granule {"temporal[]" "2010-12-12T12:00:00,"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal datetime is invalid: Invalid format: .* is too short, should be in yyyy-MM-ddTHH:mm:ssZ format." body))))))
  (testing "search by invalid temporal start-date after end-date."
    (try
      (search/find-refs :granule {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" body)))))))

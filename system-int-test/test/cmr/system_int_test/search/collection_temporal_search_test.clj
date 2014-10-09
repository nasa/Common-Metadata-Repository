(ns ^{:doc "Integration test for CMR collection temporal search"}
  cmr.system-int-test.search.collection-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "CMR_T_PROV"}))

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
                                                 :single-date-time "1999-05-01T00:00:00Z"}))]
    (index/refresh-elastic-index)

    (testing "search by temporal_start."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2010-12-12T12:00:00Z,"})]
        (is (d/refs-match? [coll2 coll3 coll4 coll5 coll6 coll7 coll8] references))))
    (testing "search by temporal_end."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ",2010-12-12T12:00:00Z"})]
        (is (d/refs-match? [coll1 coll2 coll3 coll4 coll6 coll7 coll10 coll11] references))))
    (testing "search by temporal_range."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2010-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
        (is (d/refs-match? [coll1] references))))
    (testing "search by multiple temporal_range."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]})]
        (is (d/refs-match? [coll1 coll2 coll6] references))))
    (testing "search by multiple temporal_range, options :or."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                                          "options[temporal][or]" ""})]
        (is (d/refs-match? [coll1 coll2 coll6] references))))
    (testing "search by multiple temporal_range, options :and."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2009-02-22T10:00:00Z,2010-02-22T10:00:00Z"]
                                          "options[temporal][and]" "true"})]
        (is (d/refs-match? [coll1] references))))

    (testing "search by temporal date-range with aql"
      (are [items start-date stop-date]
           (d/refs-match? items (search/find-refs-with-aql :collection [{:temporal {:start-date start-date
                                                                                    :stop-date stop-date}}]))

           ;; search by temporal_start
           [coll2 coll3 coll4 coll5 coll6 coll7 coll8] "2010-12-12T12:00:00Z" nil

           ;; search by temporal_range
           [coll1] "2010-01-01T10:00:00Z" "2010-01-10T12:00:00Z"
           [coll2 coll6 coll10] "2010-04-01T10:00:00Z" "2010-06-10T12:00:00Z"))))


;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search by invalid temporal format."
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2010-13-12T12:00:00,"})]
      (is (= 400 status))
      (is (re-find #"temporal start datetime is invalid: \[2010-13-12T12:00:00\] is not a valid datetime" (first errors)))))
  (testing "search by invalid temporal start-date after end-date."
    (let [{:keys [status errors]} (search/find-refs :collection {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
      (is (= 400 status))
      (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" (first errors))))))

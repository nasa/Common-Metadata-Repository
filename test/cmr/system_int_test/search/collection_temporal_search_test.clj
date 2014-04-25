(ns ^{:doc "Integration test for CMR collection temporal search"}
  cmr.system-int-test.search.collection-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2" "CMR_T_PROV"))

(deftest search-by-temporal
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset1"
                                                    :beginning-date-time "2010-01-01T12:00:00Z"
                                                    :ending-date-time "2010-01-11T12:00:00Z"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset2"
                                                    :beginning-date-time "2010-01-31T12:00:00Z"
                                                    :ending-date-time "2010-12-12T12:00:00Z"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset3"
                                                    :beginning-date-time "2010-12-03T12:00:00Z"
                                                    :ending-date-time "2010-12-20T12:00:00Z"}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset4"
                                                    :beginning-date-time "2010-12-12T12:00:00Z"
                                                    :ending-date-time "2011-01-03T12:00:00Z"}))
        coll5 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset5"
                                                    :beginning-date-time "2011-02-01T12:00:00Z"
                                                    :ending-date-time "2011-03-01T12:00:00Z"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset6"
                                                    :beginning-date-time "2010-01-30T12:00:00Z"}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset7"
                                                    :beginning-date-time "2010-12-12T12:00:00Z"}))
        coll8 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset8"
                                                    :beginning-date-time "2011-12-13T12:00:00Z"}))
        coll9 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "Dataset9"}))]
    (index/flush-elastic-index)

    (testing "search by temporal_start."
      (let [references (search/find-refs :collection
                                         {"temporal[]" "2010-12-12T12:00:00Z,"})]
        (is (d/refs-match? [coll2 coll3 coll4 coll5 coll6 coll7 coll8] references))))
    (testing "search by temporal_end."
      (let [references (search/find-refs :collection
                                         {"temporal[]" ",2010-12-12T12:00:00Z"})]
        (is (d/refs-match? [coll1 coll2 coll3 coll4 coll6 coll7] references))))
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
        (is (d/refs-match? [coll1] references))))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search by invalid temporal format."
    (try
      (search/find-refs :collection {"temporal[]" "2010-12-12T12:00:00,"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal datetime is invalid:.*" body))))))
  (testing "search by invalid temporal start-date after end-date."
    (try
      (search/find-refs :collection {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" body)))))))

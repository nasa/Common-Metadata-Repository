(ns ^{:doc "Integration test for CMR collection periodic temporal search"}
  cmr.system-int-test.search.collection-periodic-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]))

(def provider-collections
  {"CMR_PROV1" [{:entry-title "Dataset1"
                 :beginning-date-time "2000-01-01T12:00:00Z"
                 :ending-date-time "2000-02-14T12:00:00Z"}
                {:entry-title "Dataset2"
                 :beginning-date-time "2000-02-14T12:00:00Z"
                 :ending-date-time "2000-02-15T12:00:00Z"}
                {:entry-title "Dataset3"
                 :beginning-date-time "2000-03-15T12:00:00Z"
                 :ending-date-time "2000-04-15T12:00:00Z"}
                {:entry-title "Dataset4"
                 :beginning-date-time "2000-04-01T12:00:00Z"
                 :ending-date-time "2000-04-15T12:00:00Z"}
                {:entry-title "Dataset5"
                 :beginning-date-time "2001-01-01T12:00:00Z"
                 :ending-date-time "2001-01-31T12:00:00Z"}
                {:entry-title "Dataset6"
                 :beginning-date-time "2001-01-01T12:00:00Z"
                 :ending-date-time "2001-02-14T12:00:00Z"}
                {:entry-title "Dataset7"
                 :beginning-date-time "2001-03-15T12:00:00Z"
                 :ending-date-time "2001-04-15T12:00:00Z"}
                {:entry-title "Dataset8"
                 :beginning-date-time "2001-04-01T12:00:00Z"
                 :ending-date-time "2001-04-15T12:00:00Z"}
                {:entry-title "Dataset9"
                 :beginning-date-time "2002-01-01T12:00:00Z"
                 :ending-date-time "2002-01-31T12:00:00Z"}
                {:entry-title "Dataset10"
                 :beginning-date-time "2002-01-01T12:00:00Z"
                 :ending-date-time "2002-02-14T12:00:00Z"}
                {:entry-title "Dataset11"
                 :beginning-date-time "2002-03-14T12:00:00Z"
                 :ending-date-time "2002-04-15T12:00:00Z"}
                {:entry-title "Dataset12"
                 :beginning-date-time "2002-03-15T12:00:00Z"
                 :ending-date-time "2002-04-15T12:00:00Z"}
                {:entry-title "Dataset13"
                 :beginning-date-time "2002-04-01T12:00:00Z"
                 :ending-date-time "2002-04-15T12:00:00Z"}
                {:entry-title "Dataset14"
                 :beginning-date-time "1999-02-15T12:00:00Z"
                 :ending-date-time "1999-03-15T12:00:00Z"}
                {:entry-title "Dataset15"
                 :beginning-date-time "2003-02-15T12:00:00Z"
                 :ending-date-time "2003-03-15T12:00:00Z"}]

   "CMR_PROV2" [{:entry-title "Dataset16"
                 :beginning-date-time "1999-02-15T12:00:00Z"}
                {:entry-title "Dataset17"
                 :beginning-date-time "2001-02-15T12:00:00Z"}
                {:entry-title "Dataset18"
                 :beginning-date-time "2002-03-15T12:00:00Z"}
                {:entry-title "Dataset19"
                 :beginning-date-time "2001-11-15T12:00:00Z"
                 :ending-date-time "2001-12-15T12:00:00Z"}
                {:entry-title "Dataset20"}]})
(defn setup
  "set up the fixtures for test"
  []
  (ingest/reset)
  (doseq [provider-id (keys provider-collections)]
    (ingest/create-provider provider-id))
  (doseq [[provider-id collections] provider-collections
          collection collections]
    (ingest/update-collection provider-id collection))
  (index/flush-elastic-index))

(defn teardown
  "tear down after the test"
  []
 (ingest/reset))

(defn wrap-setup
  [f]
  (setup)
  (try
    (f)
    (finally (teardown))))

(use-fixtures :once wrap-setup)

(deftest search-by-periodic-temporal
  (testing "search by both start-day and end-day."
    (let [references (search/find-collection-refs
                       {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32, 90"})]
      (is (= 8 (count references)))
      (is (= #{"Dataset2" "Dataset3" "Dataset6" "Dataset7"
              "Dataset10" "Dataset11" "Dataset16" "Dataset17"}
            (set (map #(:dataset-id %) references))))))
  (testing "search by end-day."
    (let [references (search/find-collection-refs
                       {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, , 90"})]
      (is (= 10 (count references)))
      (is (= #{"Dataset2" "Dataset3" "Dataset5" "Dataset6" "Dataset7"
              "Dataset9" "Dataset10" "Dataset11" "Dataset16" "Dataset17"}
            (set (map #(:dataset-id %) references))))))
  (testing "search by start-day."
    (let [references (search/find-collection-refs
                       {"temporal[]" "2000-02-15T00:00:00Z, 2002-03-15T00:00:00Z, 32,"})]
      (is (= 11 (count references)))
      (is (= #{"Dataset2" "Dataset3" "Dataset4" "Dataset6" "Dataset7"
              "Dataset8" "Dataset10" "Dataset11" "Dataset16" "Dataset17" "Dataset19"}
            (set (map #(:dataset-id %) references))))))
  (testing "search by start-day without end_date."
    (let [references (search/find-collection-refs
                       {"temporal[]" ["2000-02-15T00:00:00Z, , 32"]})]
      (is (= 15 (count references)))
      (is (= #{"Dataset2" "Dataset3" "Dataset4" "Dataset6" "Dataset7"
              "Dataset8" "Dataset10" "Dataset11" "Dataset12" "Dataset13"
              "Dataset15" "Dataset16" "Dataset17" "Dataset18" "Dataset19"}
            (set (map #(:dataset-id %) references))))))
  (testing "search by start-day/end-day with date crossing year boundary."
    (let [references (search/find-collection-refs
                       {"temporal[]" ["2000-04-03T00:00:00Z, 2002-01-02T00:00:00Z, 93, 2"]})]
      (is (= 11 (count references)))
      (is (= #{"Dataset3" "Dataset4" "Dataset5" "Dataset6" "Dataset7" "Dataset8"
              "Dataset9" "Dataset10" "Dataset16" "Dataset17" "Dataset19"}
            (set (map #(:dataset-id %) references))))))
  (testing "search by multiple temporal."
    (let [references (search/find-collection-refs
                       {"temporal[]" ["1998-01-15T00:00:00Z, 1999-03-15T00:00:00Z, 60, 90"
                                      "2000-02-15T00:00:00Z, 2001-03-15T00:00:00Z, 40, 50"]})]
      (is (= 5 (count references)))
      (is (= #{"Dataset2" "Dataset6" "Dataset14" "Dataset16" "Dataset17"}
            (set (map #(:dataset-id %) references)))))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search with temporal_start_day and no temporal_start is invalid."
    (try
      (search/find-collection-refs {"temporal[]" ", , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_start_day must be accompanied by a temporal_start" body))))))
  (testing "search with temporal_end_day and no temporal_end is invalid."
    (try
      (search/find-collection-refs {"temporal[]" ", , , 32"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal_end_day must be accompanied by a temporal_end" body)))))))
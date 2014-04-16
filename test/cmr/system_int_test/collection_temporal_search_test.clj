(ns ^{:doc "Integration test for CMR collection temporal search"}
  cmr.system-int-test.collection-temporal-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.ingest-util :as ingest]
            [cmr.system-int-test.search-util :as search]
            [cmr.system-int-test.index-util :as index]))

(def provider-collections
  {"CMR_PROV1" [{:entry-title "Dataset1"
                 :beginning-date-time "2010-01-01T12:00:00Z"
                 :ending-date-time "2010-01-11T12:00:00Z"}
                {:entry-title "Dataset2"
                 :beginning-date-time "2010-01-31T12:00:00Z"
                 :ending-date-time "2010-12-12T12:00:00Z"}
                {:entry-title "Dataset3"
                 :beginning-date-time "2010-12-03T12:00:00Z"
                 :ending-date-time "2010-12-20T12:00:00Z"}
                {:entry-title "Dataset4"
                 :beginning-date-time "2010-12-12T12:00:00Z"
                 :ending-date-time "2011-01-03T12:00:00Z"}
                {:entry-title "Dataset5"
                 :beginning-date-time "2011-02-01T12:00:00Z"
                 :ending-date-time "2011-03-01T12:00:00Z"}]

   "CMR_PROV2" [{:entry-title "Dataset6"
                 :beginning-date-time "2010-01-30T12:00:00Z"}
                {:entry-title "Dataset7"
                 :beginning-date-time "2010-12-12T12:00:00Z"}
                {:entry-title "Dataset8"
                 :beginning-date-time "2011-12-13T12:00:00Z"}
                {:entry-title "Dataset9"}]})
(defn setup
  "set up the fixtures for test"
  []
  (doseq [provider-id (keys provider-collections)]
    (ingest/create-provider provider-id))
  (doseq [[provider-id collections] provider-collections
          collection collections]
    (ingest/update-collection provider-id collection))
  (index/flush-elastic-index))

(defn teardown
  "tear down after the test"
  []
  (doseq [[provider-id collections] provider-collections
          collection collections]
    (ingest/delete-collection provider-id (:entry-title collection)))
  (doseq [provider-id (keys provider-collections)]
    (ingest/delete-provider provider-id))
  (index/flush-elastic-index))

(defn wrap-setup
  [f]
  (setup)
  (try
    (f)
    (finally (teardown))))

(use-fixtures :once wrap-setup)

(deftest search-by-temporal
  (testing "search by temporal_start."
    (let [references (search/find-collection-refs
                       {"temporal[]" "2010-12-12T12:00:00Z,"})]
      (is (= 8 (count references)))
      (some #{"Dataset2" "Dataset3" "Dataset4" "Dataset5"
              "Dataset6" "Dataset7" "Dataset8" "Dataset10"}
            (map #(:dataset-id %) references))))
  (testing "search by temporal_end."
    (let [references (search/find-collection-refs
                       {"temporal[]" ",2010-12-12T12:00:00Z"})]
      (is (= 6 (count references)))
      (some #{"Dataset1" "Dataset2" "Dataset3" "Dataset4"
              "Dataset6" "Dataset7" "Dataset9"}
            (map #(:dataset-id %) references))))
  (testing "search by temporal_range."
    (let [references (search/find-collection-refs
                       {"temporal[]" "2010-01-01T10:00:00Z,2010-01-10T12:00:00Z"})]
      (is (= 1 (count references)))
      (some #{"Dataset1"}
            (map #(:dataset-id %) references))))
  (testing "search by multiple temporal_range."
    (let [references (search/find-collection-refs
                       {"temporal[]" ["2010-01-01T10:00:00Z,2010-01-10T12:00:00Z" "2010-12-22T10:00:00Z,2010-12-30T12:00:00Z"]})]
      (is (= 4 (count references)))
      (some #{"Dataset1" "Dataset4" "Dataset6" "Dataset7"}
            (map #(:dataset-id %) references)))))

;; Just some symbolic invalid temporal testing, more complete test coverage is in unit tests
(deftest search-temporal-error-scenarios
  (testing "search by invalid temporal format."
    (try
      (search/find-collection-refs {"temporal[]" "2010-12-12T12:00:00,"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"temporal date is invalid:.*" body))))))
  (testing "search by invalid temporal start-date after end-date."
    (try
      (search/find-collection-refs {"temporal[]" "2011-01-01T10:00:00Z,2010-01-10T12:00:00Z"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-find #"start_date \[2011-01-01T10:00:00Z\] must be before end_date \[2010-01-10T12:00:00Z\]" body)))))))
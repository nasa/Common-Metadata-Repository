(ns cmr.system-int-test.search.paging-search-test
  "Tests for search paging."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]))

(def collection-count 25)

(defn setup
  "set up the fixtures for test"
  []
  (ingest/reset)
  (let [provider-id "PROV1"]
    (ingest/create-provider provider-id)
    (doseq [seq-num (range 1 (inc collection-count))]
      (ingest/update-collection provider-id (search/collection-concept provider-id seq-num))))
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

(deftest search-with-page-size
  (testing "Search with page size."
    (let [references (search/find-collection-refs {:provider "PROV1"
                                                   :page_size 5})]
      (is (= 5 (count references)))))
  (testing "Search with large page size."
    (let [references (search/find-collection-refs {:provider "PROV1"
                                                   :page_size 100})]
      (is (= collection-count (count references))))))

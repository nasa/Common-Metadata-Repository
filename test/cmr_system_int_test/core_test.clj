(ns cmr-system-int-test.core-test
  (:require [clojure.test :refer :all]
            [cmr-system-int-test.ingest-util :as ingest]
            [cmr-system-int-test.search-util :as search]
            [cmr-system-int-test.index-util :as index]))

(def collections [{:short-name "MINIMAL"
                   :version "1"
                   :long-name "A minimal valid collection"
                   :dataset-id "MinimalCollectionV1"}
                  {:short-name "One"
                   :version "2"
                   :long-name "One valid collection"
                   :dataset-id "OneCollectionV1"}
                  {:short-name "Another"
                   :version "3"
                   :long-name "Another valid collection"
                   :dataset-id "AnotherCollectionV1"}
                  ])

(def collections-2 [{:short-name "One"
                     :version "2"
                     :long-name "One valid collection"
                     :dataset-id "OneCollectionV1"}
                    {:short-name "Other"
                     :version "3"
                     :long-name "Other valid collection"
                     :dataset-id "OtherCollectionV1"}
                    ])
(defn setup
  "set up the fixtures for test"
  []
  (doseq [i collections]
    (ingest/update-collection "CMR_PROV1" i))
  (doseq [i collections-2]
    (ingest/update-collection "CMR_PROV2" i))
  ; now index the collectoin
  (index/index-catalog))

(defn teardown
  "tear down after the test"
  []
  (doseq [i collections]
    (ingest/delete-collection "CMR_PROV1" (:dataset-id i)))
  (doseq [i collections-2]
    (ingest/delete-collection "CMR_PROV2" (:dataset-id i))))

(defn wrap-setup
  [f]
  (setup)
  (try
    (f)
    (finally (teardown))))

(use-fixtures :once wrap-setup)

(deftest search-by-provider-id
  (testing "search by non-existent provider id."
    (let [references (search/find-collection-refs {:provider "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing provider id."
    (let [references (search/find-collection-refs {:provider "CMR_PROV1"})]
      (is (= 3 (count references)))
      (some #{"MinimalCollectionV1" "AnotherCollectionV1"} (map #(:dataset-id %) references)))))

(deftest search-by-dataset-id
  (testing "search by non-existent dataset id."
    (let [references (search/find-collection-refs {:dataset_id "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing dataset id."
    (let [references (search/find-collection-refs {:dataset_id "MinimalCollectionV1"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            dataset-id (:dataset-id ref)
            echo-concept-id (:echo-concept-id ref)
            location (:location ref)]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" echo-concept-id))
        (is (re-matches #"http.*/catalog-rest/echo_catalog/datasets/C[0-9]+-CMR_PROV1$" location)))))
  (testing "search by multiple dataset ids."
    (let [references (search/find-collection-refs {"dataset_id[]" ["MinimalCollectionV1", "AnotherCollectionV1"]})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"MinimalCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by dataset id across different providers."
    (let [references (search/find-collection-refs {:dataset_id "OneCollectionV1"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OneCollectionV1"} (into #{} dataset-ids))))))

(deftest search-by-short-name
  (testing "search by non-existent short name."
    (let [references (search/find-collection-refs {:short_name "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing short name."
    (let [references (search/find-collection-refs {:short_name "MINIMAL"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            dataset-id (:dataset-id ref)
            echo-concept-id (:echo-concept-id ref)
            location (:location ref)]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" echo-concept-id))
        (is (re-matches #"http.*/catalog-rest/echo_catalog/datasets/C[0-9]+-CMR_PROV1$" location))))))

(deftest search-by-version-id
  (testing "search by non-existent version id."
    (let [references (search/find-collection-refs {:version "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing version id."
    (let [references (search/find-collection-refs {:version 1})]
      (is (= 1 (count references)))
      (let [ref (first references)
            dataset-id (:dataset-id ref)
            echo-concept-id (:echo-concept-id ref)
            location (:location ref)]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" echo-concept-id))
        (is (re-matches #"http.*/catalog-rest/echo_catalog/datasets/C[0-9]+-CMR_PROV1$" location))))))

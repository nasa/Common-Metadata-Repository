(ns ^{:doc "Integration test for CMR collection search"}
  cmr.system-int-test.collection-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.ingest-util :as ingest]
            [cmr.system-int-test.search-util :as search]
            [cmr.system-int-test.index-util :as index]))

(def provider-collections
  {"CMR_PROV1" [{:short-name "MINIMAL"
                 :version "1"
                 :long-name "A minimal valid collection"
                 :dataset-id "MinimalCollectionV1"}
                {:short-name "One"
                 :version "2"
                 :long-name "One valid collection"
                 :dataset-id "OneCollectionV1"}
                {:short-name "Another"
                 :version "r1"
                 :long-name "Another valid collection"
                 :dataset-id "AnotherCollectionV1"}]

   "CMR_PROV2" [{:short-name "One"
                 :version "2"
                 :long-name "One valid collection"
                 :dataset-id "OneCollectionV1"}
                {:short-name "Other"
                 :version "r2"
                 :long-name "Other valid collection"
                 :dataset-id "OtherCollectionV1"}]})
(defn setup
  "set up the fixtures for test"
  []
  (doseq [[provider-id collections] provider-collections
          collection collections]
    (ingest/update-collection provider-id collection))
  (index/flush-elastic-index))

(defn teardown
  "tear down after the test"
  []
  (doseq [[provider-id collections] provider-collections
          collection collections]
    (ingest/delete-collection provider-id (:dataset-id collection)))
  (index/flush-elastic-index))

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
      (some #{"MinimalCollectionV1" "OneCollectionV1" "AnotherCollectionV1"}
            (map #(:dataset-id %) references))))
  (testing "search by provider id using wildcard *."
    (let [references (search/find-collection-refs {:provider "CMR_PRO*", "options[provider][pattern]" "true"})]
      (is (= 5 (count references)))
      (some #{"MinimalCollectionV1" "OneCollectionV1" "AnotherCollectionV1" "OtherCollectionV1"}
            (map #(:dataset-id %) references))))
  (testing "search by provider id using wildcard ?."
    (let [references (search/find-collection-refs {:provider "CMR_PROV?", "options[provider][pattern]" "true"})]
      (is (= 5 (count references)))
      (some #{"MinimalCollectionV1" "OneCollectionV1" "AnotherCollectionV1" "OtherCollectionV1"}
            (map #(:dataset-id %) references))))
  (testing "search by provider id case not match."
    (let [references (search/find-collection-refs {:provider "CMR_prov1"})]
      (is (= 0 (count references)))))
  (testing "search by provider id ignore case false"
    (let [references (search/find-collection-refs {:provider "CMR_prov1", "options[provider][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by provider id ignore case true."
    (let [references (search/find-collection-refs {:provider "CMR_prov1", "options[provider][ignore_case]" "true"})]
      (is (= 3 (count references)))
      (some #{"MinimalCollectionV1" "OneCollectionV1" "AnotherCollectionV1" "OtherCollectionV1"}
            (map #(:dataset-id %) references)))))

(deftest search-by-dataset-id
  (testing "search by non-existent dataset id."
    (let [references (search/find-collection-refs {:dataset_id "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing dataset id."
    (let [references (search/find-collection-refs {:dataset_id "MinimalCollectionV1"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [dataset-id concept-id location]} ref]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple dataset ids."
    (let [references (search/find-collection-refs {"dataset_id[]" ["MinimalCollectionV1", "AnotherCollectionV1"]})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"MinimalCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by dataset id across different providers."
    (let [references (search/find-collection-refs {:dataset_id "OneCollectionV1"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OneCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by dataset id using wildcard *."
    (let [references (search/find-collection-refs {:dataset_id "O*", "options[dataset_id][pattern]" "true"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 3 (count references)))
      (is (= #{"OneCollectionV1" "OtherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by dataset id case not match."
    (let [references (search/find-collection-refs {:dataset_id "minimalCollectionV1"})]
      (is (= 0 (count references)))))
  (testing "search by dataset id ignore case false."
    (let [references (search/find-collection-refs {:dataset_id "minimalCollectionV1", "options[dataset_id][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by dataset id ignore case true."
    (let [references (search/find-collection-refs {:dataset_id "minimalCollectionV1", "options[dataset_id][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{dataset-id :dataset-id} (first references)]
        (is (= "MinimalCollectionV1" dataset-id))))))

(deftest search-by-short-name
  (testing "search by non-existent short name."
    (let [references (search/find-collection-refs {:short_name "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing short name."
    (let [references (search/find-collection-refs {:short_name "MINIMAL"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [dataset-id concept-id location]} ref]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple short names."
    (let [references (search/find-collection-refs {"short_name[]" ["MINIMAL", "Another"]})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"MinimalCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by short name across different providers."
    (let [references (search/find-collection-refs {:short_name "One"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OneCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by short name using wildcard *."
    (let [references (search/find-collection-refs{:short_name "O*", "options[short_name][pattern]" "true"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 3 (count references)))
      (is (= #{"OneCollectionV1" "OtherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by short name case not match."
    (let [references (search/find-collection-refs {:short_name "minimal"})]
      (is (= 0 (count references)))))
  (testing "search by short name ignore case false."
    (let [references (search/find-collection-refs {:short_name "minimal", "options[short_name][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by short name ignore case true."
    (let [references (search/find-collection-refs {:short_name "minimal", "options[short_name][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{dataset-id :dataset-id} (first references)]
        (is (= "MinimalCollectionV1" dataset-id))))))

(deftest search-by-version-id
  (testing "search by non-existent version id."
    (let [references (search/find-collection-refs {:version "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing version id."
    (let [references (search/find-collection-refs {:version 1})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [dataset-id concept-id location]} ref]
        (is (= "MinimalCollectionV1" dataset-id))
        (is (re-matches #"C[0-9]+-CMR_PROV1" concept-id))
        ;; TODO We should return a URL in the references once the retrieval feature is implemented.
        #_(is (re-matches #"http.*/catalog-rest/echo_catalog/datasets/C[0-9]+-CMR_PROV1$" location)))))
  (testing "search by multiple versions."
    (let [references (search/find-collection-refs {"version[]" ["1", "r1"]})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"MinimalCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by version across different providers."
    (let [references (search/find-collection-refs {:version "2"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OneCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by version using wildcard *."
    (let [references (search/find-collection-refs {:version "r*", "options[version][pattern]" "true"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OtherCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by version using wildcard ?."
    (let [references (search/find-collection-refs {:version "r?", "options[version][pattern]" "true"})
          dataset-ids (map #(:dataset-id %) references)]
      (is (= 2 (count references)))
      (is (= #{"OtherCollectionV1" "AnotherCollectionV1"} (into #{} dataset-ids)))))
  (testing "search by version case not match."
    (let [references (search/find-collection-refs {:version "R1"})]
      (is (= 0 (count references)))))
  (testing "search by version ignore case false."
    (let [references (search/find-collection-refs {:version "R1", "options[version][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by version ignore case true."
    (let [references (search/find-collection-refs {:version "R1", "options[version][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{dataset-id :dataset-id} (first references)]
        (is (= "AnotherCollectionV1" dataset-id))))))

(deftest search-error-scenarios
  (testing "search by un-supported parameter."
    (try
      (search/find-collection-refs {:unsupported "dummy"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-matches #".*Parameter \[unsupported\] was not recognized.*" body))))))
  (testing "search by un-supported options."
    (try
      (search/find-collection-refs {:dataset_id "MinimalCollectionV1", "options[dataset_id][unsupported]" "true"})
      (catch clojure.lang.ExceptionInfo e
        (let [status (get-in (ex-data e) [:object :status])
              body (get-in (ex-data e) [:object :body])]
          (is (= 422 status))
          (is (re-matches #".*Option \[unsupported\] for param \[entry_title\] was not recognized.*" body)))))))

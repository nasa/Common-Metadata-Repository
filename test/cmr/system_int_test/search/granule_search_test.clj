(ns ^{:doc "Integration test for CMR granule search"}
  cmr.system-int-test.search.granule-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]))

(def provider-granules
  {"CMR_PROV1" [{:entry-title "OneCollectionV1"
                 :granule-ur "Granule1"}
                {:entry-title "OneCollectionV1"
                 :granule-ur "Granule2"}
                {:entry-title "AnotherCollectionV1"
                 :granule-ur "Granule3"}]

   "CMR_PROV2" [{:entry-title "OneCollectionV1"
                 :granule-ur "Granule4"}
                {:entry-title "OtherCollectionV1"
                 :granule-ur "Granule5"}]

   "CMR_T_PROV" [{:entry-title "TestCollection"
                  :granule-ur "Granule4"}
                 {:entry-title "TestCollection"
                  :granule-ur "SampleUR1"}
                 {:entry-title "TestCollection"
                  :granule-ur "SampleUR2"}
                 {:entry-title "TestCollection"
                  :granule-ur "sampleur3"}]})

(defn provider-collections
  "Returns the provider collections map based on the provider-granules"
  []
  (into {} (for [[provider-id granules] provider-granules]
             [provider-id (map #(hash-map :entry-title %) (set (map :entry-title granules)))])))

(defn setup
  "set up the fixtures for test"
  []
  (ingest/reset)
  (doseq [provider-id (keys provider-granules)]
    (ingest/create-provider provider-id))
  (doseq [[provider-id collections] (provider-collections)
          collection collections]
    (ingest/update-collection provider-id collection))
  (index/flush-elastic-index)
  (doseq [[provider-id granules] provider-granules
          granule granules]
    (ingest/update-granule provider-id granule))
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

(deftest search-by-provider-id
  (testing "search by non-existent provider id."
    (let [references (search/find-refs :granule {:provider "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing provider id."
    (let [references (search/find-refs :granule {:provider "CMR_PROV1"})]
      (is (= 3 (count references)))
      (is (is (= #{"Granule1" "Granule2" "Granule3"}
                 (set (map :name references)))))))
  (testing "search by provider id using wildcard *."
    (let [references (search/find-refs :granule {:provider "CMR_PRO*", "options[provider][pattern]" "true"})]
      (is (= 5 (count references)))
      (is (is (= #{"Granule1" "Granule2" "Granule3" "Granule4" "Granule5"}
                 (set (map :name references)))))))
  (testing "search by provider id using wildcard ?."
    (let [references (search/find-refs :granule {:provider "CMR_PROV?", "options[provider][pattern]" "true"})]
      (is (= 5 (count references)))
      (is (is (= #{"Granule1" "Granule2" "Granule3" "Granule4" "Granule5"}
                 (set (map :name references)))))))
  (testing "search by provider id case not match."
    (let [references (search/find-refs :granule {:provider "CMR_prov1"})]
      (is (= 0 (count references)))))
  (testing "search by provider id ignore case false"
    (let [references (search/find-refs :granule {:provider "CMR_prov1", "options[provider][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by provider id ignore case true."
    (let [references (search/find-refs :granule {:provider "CMR_prov1", "options[provider][ignore_case]" "true"})]
      (is (= 3 (count references)))
      (is (is (= #{"Granule1" "Granule2" "Granule3"}
                 (set (map :name references))))))))

(deftest search-by-dataset-id
  (testing "search by non-existent dataset id."
    (let [references (search/find-refs :granule {:dataset_id "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing dataset id."
    (let [references (search/find-refs :granule {:dataset_id "AnotherCollectionV1"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [name concept-id location]} ref]
        (is (= "Granule3" name))
        (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple dataset ids."
    (let [references (search/find-refs :granule {"dataset_id[]" ["AnotherCollectionV1", "OtherCollectionV1"]})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"Granule3" "Granule5"} (set granule-urs)))))
  (testing "search by dataset id across different providers."
    (let [references (search/find-refs :granule {:dataset_id "OneCollectionV1"})
          granule-urs (map :name references)]
      (is (= 3 (count references)))
      (is (= #{"Granule1" "Granule2" "Granule4"} (set granule-urs)))))
  (testing "search by dataset id using wildcard *."
    (let [references (search/find-refs :granule {:dataset_id "O*", "options[dataset_id][pattern]" "true"})
          granule-urs (map :name references)]
      (is (= 4 (count references)))
      (is (= #{"Granule1" "Granule2" "Granule4" "Granule5"} (set granule-urs)))))
  (testing "search by dataset id case not match."
    (let [references (search/find-refs :granule {:dataset_id "anotherCollectionV1"})]
      (is (= 0 (count references)))))
  (testing "search by dataset id ignore case false."
    (let [references (search/find-refs :granule {:dataset_id "anotherCollectionV1", "options[dataset_id][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by dataset id ignore case true."
    (let [references (search/find-refs :granule {:dataset_id "anotherCollectionV1", "options[dataset_id][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{granule-ur :name} (first references)]
        (is (= "Granule3" granule-ur))))))

(deftest search-by-granule-ur
  (testing "search by non-existent granule ur."
    (let [references (search/find-refs :granule {:granule_ur "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing granule ur."
    (let [references (search/find-refs :granule {:granule_ur "Granule1"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [name concept-id location]} ref]
        (is (= "Granule1" name))
        (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple granule urs."
    (let [references (search/find-refs :granule {"granule_ur[]" ["Granule1", "Granule2"]})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"Granule1" "Granule2"} (set granule-urs)))))
  (testing "search by granule ur across different providers."
    (let [references (search/find-refs :granule {:granule_ur "Granule4"})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"Granule4"} (set granule-urs)))))
  (testing "search by granule ur using wildcard *."
    (let [references (search/find-refs :granule
                       {:granule_ur "S*" "options[granule_ur][pattern]" "true"})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"SampleUR1" "SampleUR2"} (set granule-urs)))))
  (testing "search by granule ur case not match."
    (let [references (search/find-refs :granule {:granule_ur "sampleUR1"})]
      (is (= 0 (count references)))))
  (testing "search by granule ur ignore case false."
    (let [references (search/find-refs :granule
                       {:granule_ur "sampleUR1" "options[granule_ur][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by granule ur ignore case true."
    (let [references (search/find-refs :granule
                       {:granule_ur "sampleUR1" "options[granule_ur][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{granule-ur :name} (first references)]
        (is (= "SampleUR1" granule-ur)))))
  (testing "search by granule ur using wildcard and ignore case true."
    (let [references (search/find-refs :granule
                       {:granule_ur "sampleUR?"
                        "options[granule_ur][pattern]" "true"
                        "options[granule_ur][ignore_case]" "true"})]
      (is (= 3 (count references)))
      (is (= #{"SampleUR1" "SampleUR2" "sampleur3"}
             (set (map :name references)))))))

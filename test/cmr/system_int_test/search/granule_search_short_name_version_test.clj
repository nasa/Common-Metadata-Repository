(ns cmr.system-int-test.search.granule-search-short-name-version-test
  "Integration tests for searching by short_name and version"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]))

(def provider-granules
  {"CMR_PROV1" [{:entry-title "Collection1"
                 :short-name "OneShort"
                 :version-id "1"
                 :granule-ur "Granule1"}
                {:entry-title "Collection2"
                 :short-name "OnlyShort"
                 :version-id "1"
                 :granule-ur "Granule2"}
                {:entry-title "Collection3"
                 :short-name "OneShort"
                 :version-id "2"
                 :granule-ur "Granule3"}]

   "CMR_PROV2" [{:entry-title "Collection4"
                 :short-name "AnotherS"
                 :version-id "R3"
                 :granule-ur "Granule4"}
                {:entry-title "Collection5"
                 :short-name "AnotherT"
                 :version-id "1"
                 :granule-ur "Granule5"}
                {:entry-title "Collection6"
                 :short-name "AnotherST"
                 :version-id "20"
                 :granule-ur "Granule6"}
                {:entry-title "Collection7"
                 :short-name "OneShort"
                 :version-id "200"
                 :granule-ur "Granule7"}]
   })

(defn provider-collections
  "Returns the provider collections map based on the provider-granules"
  []
  provider-granules)

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

(deftest search-by-short-name
  (testing "search by non-existent short name."
    (let [references (search/find-refs :granule {:short_name "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing short name."
    (let [references (search/find-refs :granule {:short_name "OnlyShort"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [name concept-id location]} ref]
        (is (= "Granule2" name))
        (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple short names."
    (let [references (search/find-refs :granule {"short_name[]" ["AnotherS", "AnotherT"]})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"Granule4" "Granule5"} (set granule-urs)))))
  (testing "search by short name across different providers."
    (let [references (search/find-refs :granule {:short_name "OneShort"})
          granule-urs (map :name references)]
      (is (= 3 (count references)))
      (is (= #{"Granule1" "Granule3" "Granule7"} (set granule-urs)))))
  (testing "search by short name using wildcard *."
    (let [references (search/find-refs :granule
                       {:short_name "Ano*"
                        "options[short_name][pattern]" "true"})
          granule-urs (map :name references)]
      (is (= 3 (count references)))
      (is (= #{"Granule4" "Granule5" "Granule6"} (set granule-urs)))))
  (testing "search by short name case not match."
    (let [references (search/find-refs :granule {:short_name "onlyShort"})]
      (is (= 0 (count references)))))
  (testing "search by short name ignore case false."
    (let [references (search/find-refs :granule
                       {:short_name "onlyShort"
                        "options[short_name][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by short name ignore case true."
    (let [references (search/find-refs :granule
                       {:short_name "onlyShort"
                        "options[short_name][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{granule-ur :name} (first references)]
        (is (= "Granule2" granule-ur))))))

(deftest search-by-version
  (testing "search by non-existent version."
    (let [references (search/find-refs :granule {:version "NON_EXISTENT"})]
      (is (= 0 (count references)))))
  (testing "search by existing version."
    (let [references (search/find-refs :granule {:version "2"})]
      (is (= 1 (count references)))
      (let [ref (first references)
            {:keys [name concept-id location]} ref]
        (is (= "Granule3" name))
        (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
  (testing "search by multiple versions."
    (let [references (search/find-refs :granule {"version[]" ["2", "R3"]})
          granule-urs (map :name references)]
      (is (= 2 (count references)))
      (is (= #{"Granule3" "Granule4"} (set granule-urs)))))
  (testing "search by version across different providers."
    (let [references (search/find-refs :granule {:version "1"})
          granule-urs (map :name references)]
      (is (= 3 (count references)))
      (is (= #{"Granule1" "Granule2" "Granule5"} (set granule-urs)))))
  (testing "search by version using wildcard *."
    (let [references (search/find-refs :granule
                       {:version "2*"
                        "options[version][pattern]" "true"})
          granule-urs (map :name references)]
      (is (= 3 (count references)))
      (is (= #{"Granule3" "Granule6" "Granule7"} (set granule-urs)))))
  (testing "search by version using wildcard ?."
    (let [references (search/find-refs :granule
                       {:version "2?"
                        "options[version][pattern]" "true"})
          granule-urs (map :name references)]
      (is (= 1 (count references)))
      (is (= #{"Granule6"} (set granule-urs)))))
  (testing "search by version case not match."
    (let [references (search/find-refs :granule {:version "r3"})]
      (is (= 0 (count references)))))
  (testing "search by version ignore case false."
    (let [references (search/find-refs :granule
                       {:version "r3"
                        "options[version][ignore_case]" "false"})]
      (is (= 0 (count references)))))
  (testing "search by version ignore case true."
    (let [references (search/find-refs :granule
                       {:version "r3"
                        "options[version][ignore_case]" "true"})]
      (is (= 1 (count references)))
      (let [{granule-ur :name} (first references)]
        (is (= "Granule4" granule-ur))))))

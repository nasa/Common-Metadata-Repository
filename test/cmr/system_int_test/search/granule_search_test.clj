(ns ^{:doc "Integration test for CMR granule search"}
  cmr.system-int-test.search.granule-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.common.services.messages :as msg]
            [cmr.search.validators.messages :as vmsg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2" "CMR_T_PROV"))

(comment
  (ingest/reset)
  (doseq [p ["CMR_PROV1" "CMR_PROV2" "CMR_T_PROV"]]
    (ingest/create-provider p))

  )

(deftest search-by-provider-id
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "Granule4"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "Granule5"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent provider id."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "NON_EXISTENT"})]
        (is (= 0 (count refs)))))
    (testing "search by existing provider id."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_PROV1"})]
        (is (= 3 (count refs)))
        (is (is (= #{"Granule1" "Granule2" "Granule3"}
                   (set (map :name refs)))))))
    (testing "search by provider id using wildcard *."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_PRO*", "options[provider][pattern]" "true"})]
        (is (= 5 (count refs)))
        (is (is (= #{"Granule1" "Granule2" "Granule3" "Granule4" "Granule5"}
                   (set (map :name refs)))))))
    (testing "search by provider id using wildcard ?."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_PROV?", "options[provider][pattern]" "true"})]
        (is (= 5 (count refs)))
        (is (is (= #{"Granule1" "Granule2" "Granule3" "Granule4" "Granule5"}
                   (set (map :name refs)))))))
    (testing "search by provider id case not match."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_prov1"})]
        (is (= 0 (count refs)))))
    (testing "search by provider id ignore case false"
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_prov1", "options[provider][ignore-case]" "false"})]
        (is (= 0 (count refs)))))
    (testing "search by provider id ignore case true."
      (let [{:keys [refs]} (search/find-refs :granule {:provider "CMR_prov1", "options[provider][ignore-case]" "true"})]
        (is (= 3 (count refs)))
        (is (is (= #{"Granule1" "Granule2" "Granule3"}
                   (set (map :name refs)))))))))

(deftest search-by-dataset-id
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "OneCollectionV1"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "AnotherCollectionV1"}))
        coll3 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "OneCollectionV1"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:entry-title "OtherCollectionV1"}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule3"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll3 {:granule-ur "Granule4"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll4 {:granule-ur "Granule5"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent dataset id."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "NON_EXISTENT"})]
        (is (= 0 (count refs)))))
    (testing "search by existing dataset id."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "AnotherCollectionV1"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule3" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple dataset ids."
      (let [{:keys [refs]} (search/find-refs :granule {"dataset-id[]" ["AnotherCollectionV1", "OtherCollectionV1"]})
            granule-urs (map :name refs)]
        (is (= 2 (count refs)))
        (is (= #{"Granule3" "Granule5"} (set granule-urs)))))
    (testing "search by dataset id across different providers."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "OneCollectionV1"})
            granule-urs (map :name refs)]
        (is (= 3 (count refs)))
        (is (= #{"Granule1" "Granule2" "Granule4"} (set granule-urs)))))
    (testing "search by dataset id using wildcard *."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "O*", "options[dataset-id][pattern]" "true"})
            granule-urs (map :name refs)]
        (is (= 4 (count refs)))
        (is (= #{"Granule1" "Granule2" "Granule4" "Granule5"} (set granule-urs)))))
    (testing "search by dataset id case not match."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "anotherCollectionV1"})]
        (is (= 0 (count refs)))))
    (testing "search by dataset id ignore case false."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "anotherCollectionV1", "options[dataset-id][ignore-case]" "false"})]
        (is (= 0 (count refs)))))
    (testing "search by dataset id ignore case true."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "anotherCollectionV1", "options[dataset-id][ignore-case]" "true"})]
        (is (= 1 (count refs)))
        (let [{granule-ur :name} (first refs)]
          (is (= "Granule3" granule-ur)))))))


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

(deftest search-by-granule-ur
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "Granule3"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "SampleUR1"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "SampleUR2"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "sampleur3"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent granule ur."
      (let [{:keys [refs]} (search/find-refs :granule {:granule-ur "NON_EXISTENT"})]
        (is (= 0 (count refs)))))
    (testing "search by existing granule ur."
      (let [{:keys [refs]} (search/find-refs :granule {:granule-ur "Granule1"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule1" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple granule urs."
      (let [{:keys [refs]} (search/find-refs :granule {"granule-ur[]" ["Granule1", "Granule2"]})
            granule-urs (map :name refs)]
        (is (= 2 (count refs)))
        (is (= #{"Granule1" "Granule2"} (set granule-urs)))))
    (testing "search by granule ur across different providers."
      (let [{:keys [refs]} (search/find-refs :granule {:granule-ur "Granule3"})
            granule-urs (map :name refs)]
        (is (= 2 (count refs)))
        (is (= #{"Granule3"} (set granule-urs)))))
    (testing "search by granule ur using wildcard *."
      (let [{:keys [refs]} (search/find-refs :granule
                                         {:granule-ur "S*" "options[granule-ur][pattern]" "true"})
            granule-urs (map :name refs)]
        (is (= 2 (count refs)))
        (is (= #{"SampleUR1" "SampleUR2"} (set granule-urs)))))
    (testing "search by granule ur case not match."
      (let [{:keys [refs]} (search/find-refs :granule {:granule-ur "sampleUR1"})]
        (is (= 0 (count refs)))))
    (testing "search by granule ur ignore case false."
      (let [{:keys [refs]} (search/find-refs :granule
                                         {:granule-ur "sampleUR1" "options[granule-ur][ignore-case]" "false"})]
        (is (= 0 (count refs)))))
    (testing "search by granule ur ignore case true."
      (let [{:keys [refs]} (search/find-refs :granule
                                         {:granule-ur "sampleUR1" "options[granule-ur][ignore-case]" "true"})]
        (is (= 1 (count refs)))
        (let [{granule-ur :name} (first refs)]
          (is (= "SampleUR1" granule-ur)))))
    (testing "search by granule ur using wildcard and ignore case true."
      (let [{:keys [refs]} (search/find-refs :granule
                                         {:granule-ur "sampleUR?"
                                          "options[granule-ur][pattern]" "true"
                                          "options[granule-ur][ignore-case]" "true"})]
        (is (= 3 (count refs)))
        (is (= #{"SampleUR1" "SampleUR2" "sampleur3"}
               (set (map :name refs))))))))

(deftest search-by-cloud-cover
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 0.8}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 30.0}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 120}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:cloud-cover -60.0}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:cloud-cover 0.0}))
        gran6 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "sampleur3"}))]
    (index/flush-elastic-index)
    (testing "search granules with lower bound cloud-cover value"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" "0.2,"} [gran1 gran2 gran3]))
    (testing "search granules with upper bound cloud-cover value"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" ",0.7"} [gran4 gran5]))
    (testing "search by cloud-cover range values that would not cover all granules in store"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" "-70.0,31.0"} [gran1 gran2 gran4 gran5]))
    (testing "search by cloud-cover range values that would not cover all granules in store"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" "-70.0,120.0"} [gran1 gran2 gran3 gran4 gran5]))
    (testing "search by cloud-cover with min value greater than max value"
      (let [min-value 30.0
            max-value 0.0]
        (is (= {:status 422
                :errors [(vmsg/min-value-greater-than-max min-value max-value)]}
               (search/find-refs :granule {"cloud_cover" (format "%s,%s" min-value max-value)})))))
    (testing "search by cloud-cover with non numeric str 'c9c,'"
      (let [num-range "c9c,"]
        (is (= {:status 422
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with non numeric str ',99c'"
      (let [num-range ",99c"]
        (is (= {:status 422
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with non numeric str ','"
      (let [num-range ","]
        (is (= {:status 422
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with empty str"
      (let [num-range ""]
        (is (= {:status 422
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with invalid range"
      (let [num-range "30,c9c"]
        (is (= {:status 422
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))))

;; exclude granules by echo_granule_id or concept_id (including parent concept_id) params
(deftest exclude-granules-by-echo-granule-n-concept-ids
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 0.8}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 30.0}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:cloud-cover 120}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:cloud-cover -60.0}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1-cid (get-in gran1 [:concept-id])
        gran2-cid (get-in gran2 [:concept-id])
        gran3-cid (get-in gran3 [:concept-id])
        gran4-cid (get-in gran4 [:concept-id])
        dummy-cid "D1000000004-PROV2"]
    (index/flush-elastic-index)
    (testing "fetch all granules with cloud-cover attrib"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" "-70,120"} [gran1 gran2 gran3 gran4]))
    (testing "fetch all granules with cloud-cover attrib to exclude a single granule from the set"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {:exclude {:echo_granule_id [gran1-cid]}, :cloud_cover "-70,120"} [gran2 gran3 gran4]))
    (testing "fetch all granules with cloud-cover attrib to exclude multiple granules from the set"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {:exclude {:echo_granule_id [gran1-cid gran2-cid]}, :cloud_cover "-70,120"} [gran3 gran4]))
    (testing "fetch granules by echo granule ids to exclude multiple granules from the set"
      (are [srch-params items] (d/refs-match? items (search/find-refs :granule srch-params))
           {:exclude {:echo_granule_id [gran1-cid gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]} [gran3]))
    (testing "fetch granules by echo granule ids to exclude multiple granules from the set by concept_id"
      (are [srch-params items] (d/refs-match? items (search/find-refs :granule srch-params))
           {:exclude {:concept_id [gran1-cid gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]} [gran3]))
    (testing "fetch granules by echo granule ids to exclude multiple granules from the set by parent concept_id"
      (are [srch-params items] (d/refs-match? items (search/find-refs :granule srch-params))
           {:exclude {:concept_id [coll1-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid gran4-cid]} [gran4]))
    (testing "fetch granules by echo granule ids to exclude a granule by invalid exclude param - dataset_id"
      (let [srch-params {:exclude {:dataset-id [gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]}]
        (is (= {:status 422
                :errors [(msg/invalid-exclude-param-msg :dataset-id)]}
               (search/find-refs :granule srch-params)))))))


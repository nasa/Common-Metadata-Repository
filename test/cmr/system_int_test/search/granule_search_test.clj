(ns ^{:doc "Integration test for CMR granule search"}
  cmr.system-int-test.search.granule-search-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
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
      (is (d/refs-match?
            []
            (search/find-refs :granule {:provider "NON_EXISTENT"}))))
    (testing "search by existing provider id."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "CMR_PROV1"}))))
    (testing "search by provider id using wildcard *."
      (is (d/refs-match?
            [gran1 gran2 gran3 gran4 gran5]
            (search/find-refs :granule {:provider "CMR_PRO*"
                                        "options[provider][pattern]" "true"}))))
    (testing "search by provider id using wildcard ?."
      (is (d/refs-match?
            [gran1 gran2 gran3 gran4 gran5]
            (search/find-refs :granule {:provider "CMR_PROV?"
                                        "options[provider][pattern]" "true"}))))
    (testing "search by provider id defaut is ignroe case true."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "CMR_prov1"}))))
    (testing "search by provider id ignore case false"
      (is (d/refs-match?
            []
            (search/find-refs :granule {:provider "CMR_prov1"
                                        "options[provider][ignore-case]" "false"}))))
    (testing "search by provider id ignore case true."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "CMR_prov1"
                                        "options[provider][ignore-case]" "true"}))))))

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
      (is (d/refs-match?
            []
            (search/find-refs :granule {:dataset-id "NON_EXISTENT"}))))
    (testing "search by existing dataset id."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "AnotherCollectionV1"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule3" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple dataset ids."
      (is (d/refs-match?
            [gran3 gran5]
            (search/find-refs :granule {"dataset-id[]" ["AnotherCollectionV1", "OtherCollectionV1"]}))))
    (testing "search by dataset id across different providers."
      (is (d/refs-match?
            [gran1 gran2 gran4]
            (search/find-refs :granule {:dataset-id "OneCollectionV1"}))))
    (testing "search by dataset id using wildcard *."
      (is (d/refs-match?
            [gran1 gran2 gran4 gran5]
            (search/find-refs :granule {:dataset-id "O*"
                                        "options[dataset-id][pattern]" "true"}))))
    (testing "search by dataset id default is ignore case true."
      (is (d/refs-match?
            [gran3]
            (search/find-refs :granule {:dataset-id "anotherCollectionV1"}))))
    (testing "search by dataset id ignore case false."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:dataset-id "anotherCollectionV1"
                                        "options[dataset-id][ignore-case]" "false"}))))
    (testing "search by dataset id ignore case true."
      (is (d/refs-match?
            [gran3]
            (search/find-refs :granule {:dataset-id "anotherCollectionV1"
                                        "options[dataset-id][ignore-case]" "true"}))))))


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
        gran6 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "SampleUR2"}))
        gran7 (d/ingest "CMR_PROV2" (dg/granule coll2 {:granule-ur "sampleur3"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent granule ur."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:granule-ur "NON_EXISTENT"}))))
    (testing "search by existing granule ur."
      (let [{:keys [refs]} (search/find-refs :granule {:granule-ur "Granule1"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule1" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple granule urs."
      (is (d/refs-match?
            [gran1 gran2]
            (search/find-refs :granule {"granule-ur[]" ["Granule1", "Granule2"]}))))
    (testing "search by granule ur across different providers."
      (is (d/refs-match?
            [gran3 gran4]
            (search/find-refs :granule {:granule-ur "Granule3"}))))
    (testing "search by granule ur using wildcard *."
      (is (d/refs-match?
            [gran5 gran6 gran7]
            (search/find-refs :granule {:granule-ur "S*"
                                        "options[granule-ur][pattern]" "true"}))))
    (testing "search by granule ur default is ignore case true."
      (is (d/refs-match?
            [gran5]
            (search/find-refs :granule {:granule-ur "sampleUR1"}))))
    (testing "search by granule ur ignore case false."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:granule-ur "sampleUR1"
                                        "options[granule-ur][ignore-case]" "false"}))))
    (testing "search by granule ur ignore case true."
      (is (d/refs-match?
            [gran5]
            (search/find-refs :granule {:granule-ur "sampleUR1"
                                        "options[granule-ur][ignore-case]" "true"}))))
    (testing "search by granule ur using wildcard and ignore case true."
      (is (d/refs-match?
            [gran5 gran6 gran7]
            (search/find-refs :granule {:granule-ur "sampleUR?"
                                        "options[granule-ur][pattern]" "true"
                                        "options[granule-ur][ignore-case]" "true"}))))))

(defn- make-catalog-rest-style-query
  "Make a cloud-cover query in the catalog-reset style."
  [min max]
  (cond
    (and min max)
    {"cloud-cover[min]" min "cloud-cover[max]" max}

    min
    {"cloud-cover[min]" min}

    max
    {"cloud-cover[max]" max}

    :else
    (throw (Exception. "Tests must specify either max or min cloud-cover."))))


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
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "catalog-rest style"
      (are [min max items]
           (d/refs-match? items (search/find-refs :granule (make-catalog-rest-style-query min max)))
           0.2 nil [gran1 gran2 gran3]
           nil 0.7 [gran4 gran5]
           -70.0 31.0 [gran1 gran2 gran4 gran5]))))


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
        gran4-cid (get-in gran4 [:concept-id])]
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
    (testing "fetch granules by echo granule ids to exclude a granule by invalid exclude param - dataset_id"
      ;; dataset-id aliases to entry-title - there is no easy way to recover original search param on error
      (let [srch1 {:exclude {:dataset-id [gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]}
            srch2 {:exclude {:dataset-id [gran2-cid] :concept-id [gran1-cid]}, :echo_granule_id [gran1-cid gran2-cid]}]
        (is (= {:status 422
                :errors [(msg/invalid-exclude-param-msg #{:entry-title})]}
               (search/find-refs :granule srch1)))
        (is (= {:status 422
                :errors [(msg/invalid-exclude-param-msg #{:entry-title})]}
               (search/find-refs :granule srch2)))))))

;; Find granules by echo_granule_id, echo_collection_id and concept_id params
(deftest echo-granule-id-search-test
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection))
        coll2 (d/ingest "CMR_PROV2" (dc/collection))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1-cid (get-in gran1 [:concept-id])
        gran2-cid (get-in gran2 [:concept-id])
        gran3-cid (get-in gran3 [:concept-id])
        gran4-cid (get-in gran4 [:concept-id])
        gran5-cid (get-in gran5 [:concept-id])
        dummy-cid "D1000000004-PROV2"]
    (index/flush-elastic-index)
    (testing "echo granule id search"
      (are [items cid options]
           (let [params (merge {:echo_granule_id cid}
                               (when options
                                 {"options[echo_granule_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}
           [gran1 gran5] [gran1-cid gran5-cid] {:and false}
           [] [gran1-cid gran5-cid] {:and true}
           [] (s/lower-case gran1-cid) {:ignore-case false}))
    (testing "echo granule id search - disallow ignore case"
      (is (= {:status 422
              :errors [(msg/invalid-ignore-case-opt-setting-msg #{:concept-id :echo-collection-id :echo-granule-id})]}
             (search/find-refs :granule {:echo_granule_id gran1-cid "options[echo_granule_id]" {:ignore_case true}}))))
    (testing "Search with wildcards in echo_granule_id param not supported."
      (is (= {:status 422
              :errors [(msg/invalid-pattern-opt-setting-msg #{:concept-id :echo-collection-id :echo-granule-id})]}
             (search/find-refs :granule {:echo_granule_id "G*" "options[echo_granule_id]" {:pattern true}}))))
    (testing "search granules by echo collection id"
      (are [items cid options]
           (let [params (merge {:echo_collection_id cid}
                               (when options
                                 {"options[echo_collection_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran3] coll1-cid {}
           [gran4 gran5] coll2-cid {}))
    (testing "search granules by parent concept id"
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran3] coll1-cid {}
           [gran4 gran5] coll2-cid {}))
    (testing "search granules by concept id"
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))
           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}
           [] [gran1-cid gran5-cid] {:and true}))
    (testing "Search with wildcards in concept_id param not supported."
      (is (= {:status 422
              :errors [(msg/invalid-pattern-opt-setting-msg #{:concept-id :echo-collection-id :echo-granule-id})]}
             (search/find-refs :granule {:concept_id "G*" "options[concept_id]" {:pattern true}}))))))

(ns cmr.system-int-test.search.granule-search-producer-granule-id-test
  "Integration tests for searching by producer granule id"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-producer-granule-id
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV2" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:producer-gran-id "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:producer-gran-id "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:producer-gran-id "SpecialOne"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll2 {:producer-gran-id "SpecialOne"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll2 {:producer-gran-id "Granule15"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent producer granule id."
      (let [references (search/find-refs :granule {:producer_granule_id "NON_EXISTENT"})]
        (is (d/refs-match? [] references))))
    (testing "search by existing producer granule id."
      (let [references (search/find-refs :granule {:producer_granule_id "Granule1"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by multiple producer granule ids."
      (let [references (search/find-refs :granule {"producer_granule_id[]" ["Granule1", "Granule2"]})]
        (is (d/refs-match? [gran1 gran2] references))))
    (testing "search by producer granule id across different providers."
      (let [references (search/find-refs :granule {:producer_granule_id "SpecialOne"})]
        (is (d/refs-match? [gran3 gran4] references))))
    (testing "search by producer granule id using wildcard *."
      (let [references (search/find-refs :granule
                                         {:producer_granule_id "Gran*"
                                          "options[producer_granule_id][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran5] references))))
    (testing "search by producer granule id using wildcard ?."
      (let [references (search/find-refs :granule
                                         {:producer_granule_id "Granule?"
                                          "options[producer_granule_id][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2] references))))
    (testing "search by producer granule id case not match."
      (let [references (search/find-refs :granule {:producer_granule_id "granule1"})]
        (is (d/refs-match? [] references))))
    (testing "search by producer granule id ignore case false."
      (let [references (search/find-refs :granule
                                         {:producer_granule_id "granule1"
                                          "options[producer_granule_id][ignore_case]" "false"})]
        (is (d/refs-match? [] references))))
    (testing "search by producer granule id ignore case true."
      (let [references (search/find-refs :granule
                                         {:producer_granule_id "granule1"
                                          "options[producer_granule_id][ignore_case]" "true"})]
        (is (d/refs-match? [gran1] references))))))


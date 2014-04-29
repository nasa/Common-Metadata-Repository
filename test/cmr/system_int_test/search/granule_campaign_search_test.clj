(ns cmr.system-int-test.search.granule-campaign-search-test
  "Integration test for CMR granule temporal search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-by-campaign
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :project-refs ["ABC"]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                       :project-refs ["ABC" "XYZ"]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule3"
                                                       :project-refs ["PDQ" "RST"]}))]
    (index/flush-elastic-index)

    (testing "search by single campaign single result."
      (let [references (search/find-refs :granule
                                         {"campaign" "XYZ"})]
        (is (d/refs-match? [gran2] references))))
    (testing "search by single campaign multiple result."
      (let [references (search/find-refs :granule
                                         {"campaign" "ABC"})]
        (is (d/refs-match? [gran1 gran2] references))))
    (testing "serch by single project single result."
      (let [references (search/find-refs :granule
                                         {"project" "XYZ"})]
        (is (d/refs-match? [gran2] references))))
    (testing "search by single project multiple results."
      (let [references (search/find-refs :granule
                                         {"project" "ABC"})]
        (is (d/refs-match? [gran1 gran2] references))))
    (testing "search by multiple projects."
      (let [references (search/find-refs :granule
                                         {"project" ["XYZ" "PDQ"]})]
        (is (d/refs-match? [gran2 gran3] references))))
    (testing "search with some missing projects."
      (let [references (search/find-refs :granule
                                         {"project" ["ABC" "LMN"]})]
        (is (d/refs-match? [gran1 gran2] references))))
    (testing "search for missing project."
      (let [references (search/find-refs :granule
                                         {"project" "LMN"})]
        (is (d/refs-match? [] references))))))
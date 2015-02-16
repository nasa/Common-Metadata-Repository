(ns cmr.system-int-test.search.granule-search-readable-granule-name-test
  "Integration tests for searching by readable granule name"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-producer-granule-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :producer-gran-id "SpecialOne"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                       :producer-gran-id "SpecialTwo"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "SpecialOne"
                                                       :producer-gran-id "NotSoSpecial"}))
        gran4 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule44"
                                                       :producer-gran-id "SuperSpecial"}))
        gran5 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "SuperSpecial"
                                                       :producer-gran-id "Granule2"}))]
    (index/wait-until-indexed)
    (testing "search by non-existent readable granule name."
      (let [references (search/find-refs :granule {:readable-granule-name "NON_EXISTENT"})]
        (is (d/refs-match? [] references))))
    (testing "search by existing readable granule name matching granule-ur."
      (let [references (search/find-refs :granule {:readable-granule-name "Granule1"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by existing readable granule name matching producer-granule-id"
      (let [references (search/find-refs :granule {:readable-granule-name "SpecialTwo"})]
        (is (d/refs-match? [gran2] references))))
    (testing "search by multiple readable granule names."
      (let [references (search/find-refs :granule {"readable-granule-name[]" ["Granule1", "Granule2"]})]
        (is (d/refs-match? [gran1 gran2 gran5] references))))
    (testing "search by readable granule name using wildcard *."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "Gran*"
                                          "options[readable-granule-name][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran4 gran5] references))))
    (testing "search by readable granule name using wildcard ?."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "Granule?"
                                          "options[readable-granule-name][pattern]" "true"})]
        (is (d/refs-match? [gran1 gran2 gran5] references))))
    (testing "search by readable granule name default is ignore case true."
      (let [references (search/find-refs :granule {:readable-granule-name "granule1"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by readable granule name ignore case false."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "granule1"
                                          "options[readable-granule-name][ignore-case]" "false"})]
        (is (d/refs-match? [] references))))
    (testing "search by readable granule name ignore case true."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name "granule1"
                                          "options[readable-granule-name][ignore-case]" "true"})]
        (is (d/refs-match? [gran1] references))))
    (testing "search by readable granule name default options."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name ["Granule2", "SuperSpecial"]})]
        (is (d/refs-match? [gran2 gran4 gran5] references))))
    (testing "search by readable granule name options :and."
      (let [references (search/find-refs :granule
                                         {:readable-granule-name ["Granule2", "SuperSpecial"]
                                          "options[readable-granule-name][and]" "true"})]
        (is (d/refs-match? [gran5] references))))))


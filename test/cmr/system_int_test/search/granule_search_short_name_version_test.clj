(ns cmr.system-int-test.search.granule-search-short-name-version-test
  "Integration tests for searching by short_name and version"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-by-short-name
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:short-name "OneShort"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:short-name "OnlyShort"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:short-name "OneShort"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:short-name "AnotherS"}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:short-name "AnotherT"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:short-name "AnotherST"}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:short-name "OneShort"}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll3 {:granule-ur "Granule3"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll4 {:granule-ur "Granule4"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll5 {:granule-ur "Granule5"}))
        gran6 (d/ingest "CMR_PROV2" (dg/granule coll6 {:granule-ur "Granule6"}))
        gran7 (d/ingest "CMR_PROV2" (dg/granule coll7 {:granule-ur "Granule7"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent short name."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:short_name "NON_EXISTENT"}))))
    (testing "search by existing short name."
      (let [{:keys [refs]} (search/find-refs :granule {:short_name "OnlyShort"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule2" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple short names."
      (is (d/refs-match?
            [gran4 gran5]
            (search/find-refs :granule {"short_name[]" ["AnotherS", "AnotherT"]}))))
    (testing "search by short name across different providers."
      (is (d/refs-match?
            [gran1 gran3 gran7]
            (search/find-refs :granule {:short_name "OneShort"}))))
    (testing "search by short name using wildcard *."
      (is (d/refs-match?
            [gran4 gran5 gran6]
            (search/find-refs :granule {:short_name "Ano*"
                                        "options[short_name][pattern]" "true"}))))
    (testing "search by short name default is ignore case true."
      (is (d/refs-match?
            [gran2]
            (search/find-refs :granule {:short_name "onlyShort"}))))
    (testing "search by short name ignore case false."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:short_name "onlyShort"
                                        "options[short_name][ignore-case]" "false"}))))
    (testing "search by short name ignore case true."
      (is (d/refs-match?
            [gran2]
            (search/find-refs :granule {:short_name "onlyShort"
                                        "options[short_name][ignore-case]" "true"}))))))

(deftest search-by-version
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {:version-id "1"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:version-id "1"}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:version-id "2"}))
        coll4 (d/ingest "CMR_PROV2" (dc/collection {:version-id "R3"}))
        coll5 (d/ingest "CMR_PROV2" (dc/collection {:version-id "1"}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:version-id "20"}))
        coll7 (d/ingest "CMR_PROV2" (dc/collection {:version-id "200"}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll3 {:granule-ur "Granule3"}))
        gran4 (d/ingest "CMR_PROV2" (dg/granule coll4 {:granule-ur "Granule4"}))
        gran5 (d/ingest "CMR_PROV2" (dg/granule coll5 {:granule-ur "Granule5"}))
        gran6 (d/ingest "CMR_PROV2" (dg/granule coll6 {:granule-ur "Granule6"}))
        gran7 (d/ingest "CMR_PROV2" (dg/granule coll7 {:granule-ur "Granule7"}))]
    (index/flush-elastic-index)
    (testing "search by non-existent version."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:version "NON_EXISTENT"}))))
    (testing "search by existing version."
      (let [{:keys [refs]} (search/find-refs :granule {:version "2"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name concept-id location]} ref]
          (is (= "Granule3" name))
          (is (re-matches #"G[0-9]+-CMR_PROV1" concept-id)))))
    (testing "search by multiple versions."
      (is (d/refs-match?
            [gran3 gran4]
            (search/find-refs :granule {"version[]" ["2", "R3"]}))))
    (testing "search by version across different providers."
      (is (d/refs-match?
            [gran1 gran2 gran5]
            (search/find-refs :granule {:version "1"}))))
    (testing "search by version using wildcard *."
      (is (d/refs-match?
            [gran3 gran6 gran7]
            (search/find-refs :granule {:version "2*"
                                        "options[version][pattern]" "true"}))))
    (testing "search by version using wildcard ?."
      (is (d/refs-match?
            [gran6]
            (search/find-refs :granule {:version "2?"
                                        "options[version][pattern]" "true"}))))
    (testing "search by version default is ignore case true."
      (is (d/refs-match?
            [gran4]
            (search/find-refs :granule {:version "r3"}))))
    (testing "search by version ignore case false."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:version "r3"
                                        "options[version][ignore-case]" "false"}))))
    (testing "search by version ignore case true."
      (is (d/refs-match?
            [gran4]
            (search/find-refs :granule {:version "r3"
                                        "options[version][ignore-case]" "true"}))))))
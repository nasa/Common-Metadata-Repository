(ns cmr.system-int-test.search.granule-search-short-name-version-test
  "Integration tests for searching by short_name and version"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-short-name
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "OneShort"}))
        coll2 (d/ingest "PROV1" (dc/collection {:short-name "OnlyShort"}))
        coll3 (d/ingest "PROV1" (dc/collection {:short-name "OneShort"}))
        coll4 (d/ingest "PROV2" (dc/collection {:short-name "AnotherS"}))
        coll5 (d/ingest "PROV2" (dc/collection {:short-name "AnotherT"}))
        coll6 (d/ingest "PROV2" (dc/collection {:short-name "AnotherST"}))
        coll7 (d/ingest "PROV2" (dc/collection {:short-name "OneShort"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule coll4 {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule coll5 {:granule-ur "Granule5"}))
        gran6 (d/ingest "PROV2" (dg/granule coll6 {:granule-ur "Granule6"}))
        gran7 (d/ingest "PROV2" (dg/granule coll7 {:granule-ur "Granule7"}))]
    (index/refresh-elastic-index)

    (testing "search granule by short name."
      (are [items names options]
           (let [params (merge {:short-name names}
                               (when options
                                 {"options[short-name]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [] "NON_EXISTENT" {}
           [gran2] "OnlyShort" {}
           [gran4 gran5] ["AnotherS", "AnotherT"] {}
           ;search across different providers
           [gran1 gran3 gran7] "OneShort" {}

           ;; pattern
           [gran4 gran5 gran6] "Ano*" {:pattern true}
           [gran4 gran5] "Another?" {:pattern true}

           ;; ignore case
           [] "onlyShort" {:ignore-case false}
           [gran2] "onlyShort" {:ignore-case true}
           [gran2] "onlyShort" {}))

    (testing "search by existing short name, verify result."
      (let [{:keys [refs]} (search/find-refs :granule {:short_name "OnlyShort"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name id location]} ref]
          (is (= "Granule2" name))
          (is (re-matches #"G[0-9]+-PROV1" id)))))

    (testing "search granule by short name with aql"
      (are [items names options]
           (let [condition (merge {:collectionShortName names} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [] "NON_EXISTENT" {}
           [gran2] "OnlyShort" {}
           [gran4 gran5] ["AnotherS", "AnotherT"] {}
           ;search across different providers
           [gran1 gran3 gran7] "OneShort" {}

           ;; pattern
           [gran4 gran5 gran6] "Ano%" {:pattern true}
           [gran4 gran5] "Another_" {:pattern true}

           ;; ignore case
           [] "onlyShort" {:ignore-case false}
           [gran2] "onlyShort" {:ignore-case true}
           [] "onlyShort" {}))))

(deftest search-by-version
  (let [coll1 (d/ingest "PROV1" (dc/collection {:version-id "1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:version-id "1"}))
        coll3 (d/ingest "PROV1" (dc/collection {:version-id "2"}))
        coll4 (d/ingest "PROV2" (dc/collection {:version-id "R3"}))
        coll5 (d/ingest "PROV2" (dc/collection {:version-id "1"}))
        coll6 (d/ingest "PROV2" (dc/collection {:version-id "20"}))
        coll7 (d/ingest "PROV2" (dc/collection {:version-id "200"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll3 {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule coll4 {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule coll5 {:granule-ur "Granule5"}))
        gran6 (d/ingest "PROV2" (dg/granule coll6 {:granule-ur "Granule6"}))
        gran7 (d/ingest "PROV2" (dg/granule coll7 {:granule-ur "Granule7"}))]
    (index/refresh-elastic-index)

    (testing "search granule by version id"
      (are [items versions options]
           (let [params (merge {:version versions}
                               (when options
                                 {"options[version]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [] "NON_EXISTENT" {}
           [gran3] "2" {}
           [gran3 gran4] ["2", "R3"] {}
           ;search across different providers
           [gran1 gran2 gran5] "1" {}

           ;; pattern
           [gran3 gran6 gran7] "2*" {:pattern true}
           [gran6] "2?" {:pattern true}

           ;; ignore case
           [] "r3" {:ignore-case false}
           [gran4] "r3" {:ignore-case true}
           [gran4] "r3" {}))


    (testing "search by existing version, verify result."
      (let [{:keys [refs]} (search/find-refs :granule {:version "2"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name id location]} ref]
          (is (= "Granule3" name))
          (is (re-matches #"G[0-9]+-PROV1" id)))))

    (testing "search granule by version id with aql"
      (are [items versions options]
           (let [condition (merge {:collectionVersionId versions} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [] "NON_EXISTENT" {}
           [gran3] "2" {}
           [gran3 gran4] ["2", "R3"] {}
           ;search across different providers
           [gran1 gran2 gran5] "1" {}

           ;; pattern
           [gran3 gran6 gran7] "2%" {:pattern true}
           [gran6] "2_" {:pattern true}

           ;; ignore case
           [] "r3" {:ignore-case false}
           [gran4] "r3" {:ignore-case true}
           [] "r3" {}))))

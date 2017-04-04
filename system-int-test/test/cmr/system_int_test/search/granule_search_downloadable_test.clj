(ns cmr.system-int-test.search.granule-search-downloadable-test
  "Integration tests for searching by downloadable"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-granule-by-downloadable
  (let [ru1 (dg/related-url {:type "GET DATA"})
        ru2 (dg/related-url {:type "GET RELATED VISUALIZATION"})
        ru3 (dg/related-url)
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru2]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru3]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru2 ru3]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1 ru2]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {}))]

    (index/wait-until-indexed)

    (testing "search by downloadable true."
      (is (d/refs-match? [gran1 gran5]
                         (search/find-refs :granule {:downloadable true}))))
    (testing "search by downloadable false."
      (is (d/refs-match? [gran2 gran3 gran4 gran6]
                         (search/find-refs :granule {:downloadable false}))))
    (testing "search by online only unset."
      (is (d/refs-match? [gran1 gran2 gran3 gran4 gran5 gran6]
                         (search/find-refs :granule {:downloadable "unset"}))))
    (testing "search by downloadable wrong value"
      (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :granule {:downloadable "wrong"}))))))

(deftest search-granule-by-online-only
  (let [ru1 (dg/related-url {:type "GET DATA"})
        ru2 (dg/related-url {:type "GET RELATED VISUALIZATION"})
        ru3 (dg/related-url)
        coll (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1]}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru2]}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru3]}))
        gran4 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru2 ru3]}))
        gran5 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {:related-urls [ru1 ru2]}))
        gran6 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll (:concept-id coll) {}))]

    (index/wait-until-indexed)

    (testing "search by online only true."
      (is (d/refs-match? [gran1 gran5]
                         (search/find-refs :granule {:online-only true}))))
    (testing "search by online only false."
      (is (d/refs-match? [gran2 gran3 gran4 gran6]
                         (search/find-refs :granule {:online-only false}))))
    (testing "search by online only unset."
      (is (d/refs-match? [gran1 gran2 gran3 gran4 gran5 gran6]
                         (search/find-refs :granule {:online-only "unset"}))))
    (testing "search by online only wrong value"
      (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :granule {:online-only "wrong"}))))

    (testing "search granule by online only with aql"
      (are [items value]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule [{:onlineOnly value}]))

           ;; it is not possible to search onlineOnly false in AQL, so we don't have a test for that
           [gran1 gran5] true
           [gran1 gran5] nil))))

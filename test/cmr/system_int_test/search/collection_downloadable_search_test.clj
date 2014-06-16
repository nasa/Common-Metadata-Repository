(ns cmr.system-int-test.search.collection-downloadable-search-test
  "Integration tests for searching by downloadable"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-collection-by-downloadable
  (let [ru1 (dc/related-url "GET DATA")
        ru2 (dc/related-url "GET RELATED VISUALIZATION")
        ru3 (dc/related-url nil)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru3]}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru2 ru3]}))
        coll5 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru1 ru2]}))
        coll6 (d/ingest "CMR_PROV1" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search by downloadable true."
      (is (d/refs-match? [coll1 coll5]
                         (search/find-refs :collection {:downloadable true}))))
    (testing "search by downloadable false."
      (is (d/refs-match? [coll2 coll3 coll4 coll6]
                         (search/find-refs :collection {:downloadable false}))))
    (testing "search by online only unset."
      (is (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {:downloadable "unset"}))))
    (testing "search by downloadable wrong value"
      (is (= {:status 422 :errors ["Parameter :downloadable must take value of true, false, or unset, but was wrong"]}
             (search/find-refs :collection {:downloadable "wrong"}))))))

(deftest search-collection-by-online-only
  (let [ru1 (dc/related-url "GET DATA")
        ru2 (dc/related-url "GET RELATED VISUALIZATION")
        ru3 (dc/related-url nil)
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru1]}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru2]}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru3]}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru2 ru3]}))
        coll5 (d/ingest "CMR_PROV1" (dc/collection {:related-urls [ru1 ru2]}))
        coll6 (d/ingest "CMR_PROV1" (dc/collection {}))]

    (index/refresh-elastic-index)

    (testing "search by online only true."
      (is (d/refs-match? [coll1 coll5]
                         (search/find-refs :collection {:online-only true}))))
    (testing "search by online only false."
      (is (d/refs-match? [coll2 coll3 coll4 coll6]
                         (search/find-refs :collection {:online-only false}))))
    (testing "search by online only unset."
      (is (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {:online-only "unset"}))))
    (testing "search by online only wrong value"
      (is (= {:status 422 :errors ["Parameter :downloadable must take value of true, false, or unset, but was wrong"]}
             (search/find-refs :collection {:online-only "wrong"}))))))

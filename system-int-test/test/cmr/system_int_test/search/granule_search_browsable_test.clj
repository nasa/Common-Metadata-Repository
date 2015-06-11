(ns cmr.system-int-test.search.granule-search-browsable-test
  "Integration tests for searching by browsable"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-granule-by-browsable-or-browse-only
  (let [ru1 (dc/related-url {:type "GET DATA"})
        ru2 (dc/related-url {:type "GET RELATED VISUALIZATION"})
        ru3 (dc/related-url)
        coll (d/ingest "PROV1" (dc/collection {}))
        g1 (d/ingest "PROV1" (dg/granule coll {:related-urls [ru1]}))
        g2 (d/ingest "PROV1" (dg/granule coll {:related-urls [ru2]}))
        g3 (d/ingest "PROV1" (dg/granule coll {:related-urls [ru3]}))
        g4 (d/ingest "PROV1" (dg/granule coll {:related-urls [ru2 ru3]}))
        g5 (d/ingest "PROV1" (dg/granule coll {:related-urls [ru1 ru2]}))
        g6 (d/ingest "PROV1" (dg/granule coll {}))]

    (index/wait-until-indexed)

    (are [value items]
         (d/refs-match? items (search/find-refs :granule {:browsable value}))
         true [g2 g4 g5]
         false [g1 g3 g6]
         "unset" [g1 g2 g3 g4 g5 g6])

    (are [value items]
         (d/refs-match? items (search/find-refs :granule {:browse-only value}))
         true [g2 g4 g5]
         false [g1 g3 g6]
         "unset" [g1 g2 g3 g4 g5 g6])

    (testing "search granule by browse_only with aql"
      (are [items value]
           (d/refs-match? items (search/find-refs-with-aql :granule [{:browseOnly value}]))
           ;; it is not possible to search browseOnly false in AQL, so we don't have a test for that
           [g2 g4 g5] true
           [g2 g4 g5] nil))

    (testing "search by browsable wrong value"
      (is (= {:status 400 :errors ["Parameter browsable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :granule {:browsable "wrong"}))))))


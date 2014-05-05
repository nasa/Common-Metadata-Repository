(ns cmr.system-int-test.search.granule-search-online-only-test
  "Integration tests for searching by online only"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(deftest search-by-online-only
  (let [ru1 (dg/related-url "GET DATA")
        ru2 (dg/related-url "GET RELATED VISUALIZATION")
        ru3 (dg/related-url nil)
        coll (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll {:related-urls [ru1]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll {:related-urls [ru2]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll {:related-urls [ru3]}))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll {:related-urls [ru2 ru3]}))
        gran5 (d/ingest "CMR_PROV1" (dg/granule coll {:related-urls [ru1 ru2]}))
        gran6 (d/ingest "CMR_PROV1" (dg/granule coll {}))]

    (index/flush-elastic-index)

    (testing "search by online only true."
      (let [references (search/find-refs :granule {:online_only "true"})]
        (is (d/refs-match? [gran1 gran5] references))))
    (testing "search by online only default"
      (let [references (search/find-refs :granule {:online_only ""})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4 gran5 gran6] references))))
    (testing "search by online only false."
      (let [references (search/find-refs :granule {:online_only "false"})]
        (is (d/refs-match? [gran2 gran3 gran4 gran6] references))))))

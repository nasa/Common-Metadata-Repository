(ns ^{:doc "Search CMR collection and granule by revision date Integration test"}
  cmr.system-int-test.search.rev-date-concept-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

#_(search/find-refs :collection {"updated_since[]" "2014-05-07T14:30:33Z,2014-05-07T14:30:34Z"})

(deftest search-colls-by-revision-date
  (let [chkpt1-tz (f/unparse (f/formatters :date-time-no-ms) (t/now))
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {}))
        _ (Thread/sleep 1000)

        chkpt2-tz (f/unparse (f/formatters :date-time-no-ms) (t/now))
        coll5 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {}))
        _ (Thread/sleep 1000)

        chkpt3-tz (f/unparse (f/formatters :date-time-no-ms) (t/now))]
    (index/flush-elastic-index)
    (testing "search for collections ingested into system after chkpt1 - as single value str"
      (let [references (search/find-refs :collection {"updated_since" chkpt1-tz})]
        (is (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6] references))))
    (testing "search for collections ingested into system after chkpt1"
      (let [references (search/find-refs :collection {"updated_since[]" chkpt1-tz})]
        (is (d/refs-match? [coll1 coll2 coll3 coll4 coll5 coll6] references))))
    (testing "search for collections ingested into system after chkpt2"
      (let [references (search/find-refs :collection {"updated_since[]" chkpt2-tz})]
        (is (d/refs-match? [coll5 coll6] references))))
    (testing "search for collections ingested into system after chkpt3"
      (let [references (search/find-refs :collection {"updated_since[]" chkpt3-tz})]
        (is (d/refs-match? [] references))))
    (testing "search for collections ingested into system with invalid open ended datetime"
      (let [{:keys [status errors]} (search/find-refs :collection {"updated_since" (format "%s," chkpt1-tz)})
            err (first errors)]
        (is (= 422 status))
        (is (re-find #"datetime is invalid:.*" err))))
    (testing "search for collections ingested into system with invalid inputs - duration chkpt1 and chkpt2"
      (let [{:keys [status errors]} (search/find-refs :collection {"updated_since[]" (format "%s,%s" chkpt1-tz chkpt2-tz)})
            err (first errors)]
        (is (= 422 status))
        (is (re-find #"datetime is invalid:.*" err))))

    ))




(deftest search-grans-by-revision-date
  (let [chkpt1-tz (f/unparse (f/formatters :date-time-no-ms) (t/now))
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll1 {}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll1 {}))
        _ (Thread/sleep 1000)
        chkpt2-tz (f/unparse (f/formatters :date-time-no-ms) (t/now))
        gran4 (d/ingest "CMR_PROV1" (dg/granule coll1 {}))]
    (index/flush-elastic-index)

    (testing "search for granules ingested into system after chkpt1"
      (let [references (search/find-refs :granule {"updated_since[]" chkpt1-tz})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4] references))))
    (testing "search for granules ingested into system after chkpt2"
      (let [references (search/find-refs :granule {"updated_since[]" chkpt2-tz})]
        (is (d/refs-match? [gran4] references))))))

(ns ^{:doc "Search CMR collection and granule by revision date Integration test"}
  cmr.system-int-test.search.rev-date-concept-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [clj-time.core :as t]
            [cmr.common.time-keeper :as tk]
            [clj-time.format :as f]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn now-time-str
  []
  (f/unparse (f/formatters :date-time) (tk/now)))

;; Tests are commented out due to CMR-539.
;; Note later: This can be fixed by the use of the new namespace cmr.common.time-keeper

#_(deftest search-colls-by-revision-date
  (let [chkpt1-tz (now-time-str)
        _ (Thread/sleep 1000)
        coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {}))
        coll3 (d/ingest "PROV1" (dc/collection {}))
        coll4 (d/ingest "PROV1" (dc/collection {}))
        _ (Thread/sleep 1000)
        chkpt2-tz (now-time-str)
        _ (Thread/sleep 1000)
        coll5 (d/ingest "PROV1" (dc/collection {}))
        coll6 (d/ingest "PROV2" (dc/collection {}))
        _ (Thread/sleep 1000)
        chkpt3-tz (now-time-str)]
    (index/refresh-elastic-index)
    (testing "search for collections ingested into system after chkpt1 - single value str"
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
        (is (= 400 status))
        (is (re-find #"datetime is invalid:.*" err))))
    (testing "search for collections ingested into system with invalid inputs - duration chkpt1 and chkpt2"
      (let [{:keys [status errors]} (search/find-refs :collection {"updated_since[]" (format "%s,%s" chkpt1-tz chkpt2-tz)})
            err (first errors)]
        (is (= 400 status))
        (is (re-find #"datetime is invalid:.*" err))))))


#_(deftest search-grans-by-revision-date
  (let [chkpt1-tz (now-time-str)
        _ (Thread/sleep 1000)
        coll1 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {}))
        _ (Thread/sleep 1000)
        chkpt2-tz (now-time-str)
        _ (Thread/sleep 1000)
        gran4 (d/ingest "PROV1" (dg/granule coll1 {}))]
    (index/refresh-elastic-index)

    (testing "search for granules ingested into system after chkpt1 - single value str"
      (let [references (search/find-refs :granule {"updated_since" chkpt1-tz})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4] references))))
    (testing "search for granules ingested into system after chkpt1"
      (let [references (search/find-refs :granule {"updated_since[]" chkpt1-tz})]
        (is (d/refs-match? [gran1 gran2 gran3 gran4] references))))
    (testing "search for granules ingested into system after chkpt2"
      (let [references (search/find-refs :granule {"updated_since[]" chkpt2-tz})]
        (is (d/refs-match? [gran4] references))))
    (testing "search for collections ingested into system with invalid open ended datetime"
      (let [{:keys [status errors]} (search/find-refs :granule {"updated_since" (format "%s," chkpt1-tz)})
            err (first errors)]
        (is (= 400 status))
        (is (re-find #"datetime is invalid:.*" err))))
    (testing "search for collections ingested into system with invalid inputs - duration chkpt1 and chkpt2"
      (let [{:keys [status errors]} (search/find-refs :granule {"updated_since[]" (format "%s,%s" chkpt1-tz chkpt2-tz)})
            err (first errors)]
        (is (= 400 status))
        (is (re-find #"datetime is invalid:.*" err))))))

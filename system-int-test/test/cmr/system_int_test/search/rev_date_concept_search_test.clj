(ns cmr.system-int-test.search.rev-date-concept-search-test
  "Integration test for CMR collection and granule search by revision date"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as u]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.system :as s]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(deftest search-collections-by-revision-date
  (s/only-with-in-memory-database
    (let [_ (dev-sys-util/freeze-time! "2000-01-01T10:00:00Z")
          coll1 (d/ingest "PROV1" (dc/collection {}))
          _ (dev-sys-util/freeze-time! "2000-02-01T10:00:00Z")
          coll2 (d/ingest "PROV1" (dc/collection {}))
          _ (dev-sys-util/freeze-time! "2000-03-01T10:00:00Z")
          coll3 (d/ingest "PROV1" (dc/collection {}))
          _ (dev-sys-util/freeze-time! "2000-04-01T10:00:00Z")
          coll4 (d/ingest "PROV1" (dc/collection {}))
          _ (dev-sys-util/freeze-time! "2015-01-01T10:00:00Z")
          coll5 (d/ingest "PROV1" (dc/collection {}))]
      (index/wait-until-indexed)

      (testing "search collections with revision_date[] ranges and options"
        (u/are2 [colls value options]
                (let [references (search/find-refs :collection
                                                   (merge {"revision_date[]" value} options))]
                  (d/refs-match? colls references))

                "range without begin - find all"
                [coll1 coll2 coll3 coll4 coll5] ",2015-01-01T10:00:00Z" {}
                "range without begin - find some"
                [coll1 coll2 coll3] ",2000-03-01T10:00:00Z" {}
                "range without begin - find none"
                [] ",2000-01-01T09:00:00Z" {}
                "range without end - find all"
                [coll1 coll2 coll3 coll4 coll5] "2000-01-01T10:00:00Z," {}
                "range without end - omitting ,"
                [coll1 coll2 coll3 coll4 coll5] "2000-01-01T10:00:00Z" {}
                "range without end - find some"
                [coll4 coll5] "2000-04-01T10:00:00Z," {}
                "range without end - find none"
                [] "2015-01-01T11:00:00Z," {}
                "range - find all"
                [coll1 coll2 coll3 coll4 coll5] "2000-01-01T10:00:00Z,2015-01-01T10:00:00Z" {}
                "range - find some"
                [coll1 coll2] "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z" {}
                "range - find none"
                [] "2000-01-01T11:00:00Z,2000-02-01T09:00:00Z" {}
                "find a single value"
                [coll1] "2000-01-01T10:00:00Z,2000-01-01T10:00:00Z" {}
                "multiple ranges without options"
                [coll3 coll4 coll5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {}
                "multiple ranges with option and false"
                [coll3 coll4 coll5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "false"}
                "multiple ranges with option and true"
                [coll4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "true"}))
      (testing "search collections with revision_date is OK"
        (are [colls param value]
             (let [references (search/find-refs :collection {param value})]
               (d/refs-match? colls references))
             [coll1 coll2] "revision_date[]" "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z"
             [coll3 coll4 coll5] "revision_date" ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]))

      (testing "search collections with updated_since"
        (are [colls param value]
             (let [references (search/find-refs :collection {param value})]
               (d/refs-match? colls references))
             [coll1 coll2 coll3 coll4 coll5] "updated_since" "2000-01-01T10:00:00Z"
             [coll1 coll2 coll3 coll4 coll5] "updated_since[]" "2000-01-01T10:00:00Z"
             [coll2 coll3 coll4 coll5] "updated_since" "2000-02-01T00:00:00Z"
             [coll5] "updated_since" "2015-01-01T10:00:00Z"
             [] "updated_since" "2015-01-01T10:00:01Z"))
      (testing "search collections with updated_since range is invalid"
        (u/are2 [value]
                (let [{:keys [status errors]} (search/find-refs :collection {"updated_since" value})
                      err (first errors)]
                  (and (= 400 status)
                       (re-find #"datetime is invalid:.*" err)))
                "range without begin" ",2000-01-01T10:00:00Z"
                "range without end" "2000-01-01T10:00:00Z,"
                "range" "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z")))))


(deftest search-granules-by-revision-date
  (s/only-with-in-memory-database
    (let [coll1 (d/ingest "PROV1" (dc/collection {}))
          _ (dev-sys-util/freeze-time! "2000-01-01T10:00:00Z")
          gran1 (d/ingest "PROV1" (dg/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-02-01T10:00:00Z")
          gran2 (d/ingest "PROV1" (dg/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-03-01T10:00:00Z")
          gran3 (d/ingest "PROV1" (dg/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-04-01T10:00:00Z")
          gran4 (d/ingest "PROV1" (dg/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2015-01-01T10:00:00Z")
          gran5 (d/ingest "PROV1" (dg/granule coll1 {}))]
      (index/wait-until-indexed)

      (testing "search granules with revision_date[] ranges and options"
        (u/are2 [grans value options]
                (let [references (search/find-refs :granule
                                                   (merge {"revision_date[]" value} options))]
                  (d/refs-match? grans references))

                "range without begin - find all"
                [gran1 gran2 gran3 gran4 gran5] ",2015-01-01T10:00:00Z" {}
                "range without begin - find some"
                [gran1 gran2 gran3] ",2000-03-01T10:00:00Z" {}
                "range without begin - find none"
                [] ",2000-01-01T09:00:00Z" {}
                "range without end - find all"
                [gran1 gran2 gran3 gran4 gran5] "2000-01-01T10:00:00Z," {}
                "range without end - omitting ,"
                [gran1 gran2 gran3 gran4 gran5] "2000-01-01T10:00:00Z" {}
                "range without end - find some"
                [gran4 gran5] "2000-04-01T10:00:00Z," {}
                "range without end - find none"
                [] "2015-01-01T11:00:00Z," {}
                "range - find all"
                [gran1 gran2 gran3 gran4 gran5] "2000-01-01T10:00:00Z,2015-01-01T10:00:00Z" {}
                "range - find some"
                [gran1 gran2] "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z" {}
                "range - find none"
                [] "2000-01-01T11:00:00Z,2000-02-01T09:00:00Z" {}
                "find a single value"
                [gran1] "2000-01-01T10:00:00Z,2000-01-01T10:00:00Z" {}
                "multiple ranges without options"
                [gran3 gran4 gran5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {}
                "multiple ranges with option and false"
                [gran3 gran4 gran5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "false"}
                "multiple ranges with option and true"
                [gran4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "true"}))
      (testing "search granules with revision_date is OK"
        (are [grans param value]
             (let [references (search/find-refs :granule {param value})]
               (d/refs-match? grans references))
             [gran1 gran2] "revision_date" "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z"
             [gran3 gran4 gran5] "revision_date" ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]))

      (testing "search granules with updated_since"
        (are [grans param value]
             (let [references (search/find-refs :granule {param value})]
               (d/refs-match? grans references))
             [gran1 gran2 gran3 gran4 gran5] "updated_since" "2000-01-01T10:00:00Z"
             [gran1 gran2 gran3 gran4 gran5] "updated_since[]" "2000-01-01T10:00:00Z"
             [gran2 gran3 gran4 gran5] "updated_since" "2000-02-01T00:00:00Z"
             [gran5] "updated_since" "2015-01-01T10:00:00Z"
             [] "updated_since" "2015-01-01T10:00:01Z"))
      (testing "search granules with updated_since range is invalid"
        (u/are2 [value]
                (let [{:keys [status errors]} (search/find-refs :granule {"updated_since" value})
                      err (first errors)]
                  (and (= 400 status)
                       (re-find #"datetime is invalid:.*" err)))
                "range without begin" ",2000-01-01T10:00:00Z"
                "range without end" "2000-01-01T10:00:00Z,"
                "range" "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z")))))

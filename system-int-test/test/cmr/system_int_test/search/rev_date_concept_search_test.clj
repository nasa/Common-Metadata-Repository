(ns cmr.system-int-test.search.rev-date-concept-search-test
  "Integration test for CMR collection and granule search by revision date"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2]]
   [cmr.system-int-test.data2.collection :as collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       (dev-sys-util/freeze-resume-time-fixture)]))

(deftest ^:in-memory-db search-collections-by-revision-date
  ;; We only test in memory mode here as this test uses time-keeper to freeze time. This will not
  ;; work for external db mode since the revision date would be set automatically by oracle
  ;; when concepts are saved and would not depend on the current time in time-keeper.
  (system/only-with-in-memory-database
    (let [_ (dev-sys-util/freeze-time! "2000-01-01T10:00:00Z")
          coll1 (data-core/ingest "PROV1" (collection/collection {}))
          _ (dev-sys-util/freeze-time! "2000-02-01T10:00:00Z")
          coll2 (data-core/ingest "PROV1" (collection/collection {}))
          _ (dev-sys-util/freeze-time! "2000-03-01T10:00:00Z")
          coll3 (data-core/ingest "PROV1" (collection/collection {}))

          ;; Collection 4 will have updates
          _ (dev-sys-util/freeze-time! "2000-04-01T10:00:00Z")
          coll4-1 (data-core/ingest "PROV1" (collection/collection {}))
          _ (dev-sys-util/freeze-time! "2000-05-01T10:00:00Z")
          coll4-2 (data-core/ingest "PROV1" (dissoc coll4-1 :revision-id))

          ;; Collection D will be deleted
          _ (dev-sys-util/freeze-time! "2000-06-01T10:00:00Z")
          coll-d-1 (data-core/ingest "PROV1" (collection/collection {}))

          _ (dev-sys-util/freeze-time! "2000-07-01T10:00:00Z")
          _ (ingest/delete-concept (data-core/item->concept coll-d-1))
          coll-d-2 (assoc coll-d-1 :deleted true :revision-id 2)

          _ (dev-sys-util/freeze-time! "2015-01-01T10:00:00Z")
          coll5 (data-core/ingest "PROV1" (collection/collection {}))]
      (index/wait-until-indexed)

      (testing "search collections with revision_date[] ranges and options"
        (are2 [colls value options]
              (let [references (search/find-refs :collection
                                                 (merge {"revision_date[]" value} options))]
                (data-core/refs-match? colls references))

              "range without begin - find all"
              [coll1 coll2 coll3 coll4-2 coll5] ",2015-01-01T10:00:00Z" {}

              "all-revisions - range without begin - find all"
              [coll1 coll2 coll3 coll4-1 coll4-2 coll-d-1 coll-d-2 coll5] ",2015-01-01T10:00:00Z" {:all-revisions true}

              "range without begin - find some"
              [coll1 coll2 coll3] ",2000-03-01T10:00:00Z" {}

              "all-revisions - range without begin - find some"
              [coll1 coll2 coll3 coll4-1 coll4-2] ",2000-05-01T10:00:00Z" {:all-revisions true}

              "range without begin - find none"
              [] ",2000-01-01T09:00:00Z" {}

              "range without end - find all"
              [coll1 coll2 coll3 coll4-2 coll5] "2000-01-01T10:00:00Z," {}

              "range without end - omitting ,"
              [coll1 coll2 coll3 coll4-2 coll5] "2000-01-01T10:00:00Z" {}

              "range without end - find some"
              [coll4-2 coll5] "2000-04-01T10:00:00Z," {}

              "all-revisions - range without end - find some"
              [coll4-1 coll4-2 coll-d-1 coll-d-2 coll5] "2000-04-01T10:00:00Z," {:all-revisions true}

              "range without end - find none"
              [] "2015-01-01T11:00:00Z," {}

              "range - find all"
              [coll1 coll2 coll3 coll4-2 coll5] "2000-01-01T10:00:00Z,2015-01-01T10:00:00Z" {}

              "range - find some"
              [coll1 coll2] "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z" {}

              "range - find none"
              [] "2000-01-01T11:00:00Z,2000-02-01T09:00:00Z" {}

              "find a single value"
              [coll1] "2000-01-01T10:00:00Z,2000-01-01T10:00:00Z" {}

              "multiple ranges without options, should default to AND false"
              [coll3 coll4-2 coll5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {}

              "multiple ranges with option and false"
              [coll3 coll4-2 coll5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "false"}

              "multiple ranges with option and true"
              [coll4-2] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "true"}))


      (testing "search collections with revision_date is OK"
        (are [colls param value]
            (let [references (search/find-refs :collection {param value})]
              (data-core/refs-match? colls references))
          [coll1 coll2] "revision_date[]" "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z"
          [coll3 coll4-2 coll5] "revision_date" ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]))

      (testing "search collections with invalid revision date"
        (are2 [value err-pattern]
              (let [{:keys [status errors]} (search/find-refs :collection {"revision_date" value})
                    err (first errors)]
                (and (= 400 status)
                     (re-find err-pattern err)))
              "invalid date time"
              "2000-01-01T10:00:99Z" #"datetime is invalid:.*"

              "too many datetimes"
              "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z,2008-04-01T10:00:00Z"
              #"Too many commas in revision-date.*"

              "too many commas"
              "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z," #"Too many commas in revision-date.*"))

      (testing "search collections with updated_since"
        (are [colls param value]
            (let [references (search/find-refs :collection {param value})]
              (data-core/refs-match? colls references))
          [coll1 coll2 coll3 coll4-2 coll5] "updated_since" "2000-01-01T10:00:00Z"
          [coll1 coll2 coll3 coll4-2 coll5] "updated_since[]" "2000-01-01T10:00:00Z"
          [coll2 coll3 coll4-2 coll5] "updated_since" "2000-02-01T00:00:00Z"
          [coll5] "updated_since" "2015-01-01T10:00:00Z"
          [] "updated_since" "2015-01-01T10:00:01Z"))
      (testing "search collections with updated_since range is invalid"
        (are2 [value]
              (let [{:keys [status errors]} (search/find-refs :collection {"updated_since" value})
                    err (first errors)]
                (and (= 400 status)
                     (re-find #"datetime is invalid:.*" err)))
              "range without begin" ",2000-01-01T10:00:00Z"
              "range without end" "2000-01-01T10:00:00Z,"
              "range" "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z"))
      (testing "search collections with multiple updated_since values is invalid"
        (let [value ["2000-01-01T10:00:00Z" "2009-01-01T10:00:00Z"]
              {:keys [status errors]} (search/find-refs :collection {"updated_since" value})]
          (is (= [400 ["Search not allowed with multiple updated_since values"]]
                 [status errors]))))

      (testing "JSON query"
        (testing "search collections with updated_since"
          (are [items search]
              (data-core/refs-match? items (search/find-refs-with-json-query :collection {} search))

            [coll1 coll2 coll3 coll4-2 coll5] {:updated_since "2000-01-01T10:00:00Z"}
            [coll2 coll3 coll4-2 coll5] {:updated_since "2000-02-01T00:00:00Z"}
            [coll5] {:updated_since "2015-01-01T10:00:00Z"}
            [] {:updated_since "2015-01-01T10:00:01Z"}))

        (testing "invalid update_time"
          (are2 [search exp-errors]
                (let [{:keys [status errors]}
                      (search/find-refs-with-json-query :collection {} search)]
                  (is (= [400 exp-errors]
                         [status errors])))

                "ranges are invalid"
                {:updated_since {:start_date "2000-01-01T10:00:00Z"
                                 :end_date "2002-01-01T10:00:00Z"}}
                ["#/condition/updated_since: expected type: String, found: JSONObject"]

                "invalid date string"
                {:updated_since "foo"}
                [(str "#/condition/updated_since: [foo] is not a valid date-time. "
                      "Expected [yyyy-MM-dd'T'HH:mm:ssZ, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}Z, "
                      "yyyy-MM-dd'T'HH:mm:ss[+-]HH:mm, yyyy-MM-dd'T'HH:mm:ss.[0-9]{1,9}[+-]HH:mm]")]))))))

(deftest ^:in-memory-db search-granules-by-revision-date
  (system/only-with-in-memory-database
    (let [coll1 (data-core/ingest "PROV1" (collection/collection {}))
          _ (dev-sys-util/freeze-time! "2000-01-01T10:00:00Z")
          gran1 (data-core/ingest "PROV1" (granule/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-02-01T10:00:00Z")
          gran2 (data-core/ingest "PROV1" (granule/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-03-01T10:00:00Z")
          gran3 (data-core/ingest "PROV1" (granule/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2000-04-01T10:00:00Z")
          gran4 (data-core/ingest "PROV1" (granule/granule coll1 {}))
          _ (dev-sys-util/freeze-time! "2015-01-01T10:00:00Z")
          gran5 (data-core/ingest "PROV1" (granule/granule coll1 {}))]
      (index/wait-until-indexed)

      (testing "search granules with revision_date[] ranges and options"
        (are2 [grans value options]
              (let [references (search/find-refs :granule
                                                 (merge {"revision_date[]" value} options))]
                (data-core/refs-match? grans references))

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
              "multiple ranges without options, should default to AND false"
              [gran3 gran4 gran5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {}
              "multiple ranges with option and false"
              [gran3 gran4 gran5] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "false"}
              "multiple ranges with option and true"
              [gran4] ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"] {"options[revision_date][and]" "true"}))
      (testing "search granules with revision_date is OK"
        (are [grans param value]
            (let [references (search/find-refs :granule {param value})]
              (data-core/refs-match? grans references))
          [gran1 gran2] "revision_date" "2000-01-01T10:00:00Z,2000-02-01T10:00:00Z"
          [gran3 gran4 gran5] "revision_date" ["2000-04-01T10:00:00Z," "2000-03-01T10:00:00Z,2000-05-01T10:00:00Z"]))
      (testing "search granules with invalid revision date"
        (are2 [value err-pattern]
              (let [{:keys [status errors]} (search/find-refs :granule {"revision_date" value})
                    err (first errors)]
                (and (= 400 status)
                     (re-find err-pattern err)))
              "invalid date time"
              "2000-01-01T10:00:99Z" #"datetime is invalid:.*"

              "too many datetimes"
              "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z,2008-04-01T10:00:00Z"
              #"Too many commas in revision-date.*"

              "too many commas"
              "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z," #"Too many commas in revision-date.*"))

      (testing "search granules with updated_since"
        (are [grans param value]
            (let [references (search/find-refs :granule {param value})]
              (data-core/refs-match? grans references))
          [gran1 gran2 gran3 gran4 gran5] "updated_since" "2000-01-01T10:00:00Z"
          [gran1 gran2 gran3 gran4 gran5] "updated_since[]" "2000-01-01T10:00:00Z"
          [gran2 gran3 gran4 gran5] "updated_since" "2000-02-01T00:00:00Z"
          [gran5] "updated_since" "2015-01-01T10:00:00Z"
          [] "updated_since" "2015-01-01T10:00:01Z"))
      (testing "search granules with updated_since range is invalid"
        (are2 [value]
              (let [{:keys [status errors]} (search/find-refs :granule {"updated_since" value})
                    err (first errors)]
                (and (= 400 status)
                     (re-find #"datetime is invalid:.*" err)))
              "range without begin" ",2000-01-01T10:00:00Z"
              "range without end" "2000-01-01T10:00:00Z,"
              "range" "2000-01-01T10:00:00Z,2000-04-01T10:00:00Z"))
      (testing "search granules with multiple updated_since values is invalid"
        (let [value ["2000-01-01T10:00:00Z" "2009-01-01T10:00:00Z"]
              {:keys [status errors]} (search/find-refs :granule {"updated_since" value})]
          (is (= [400 ["Search not allowed with multiple updated_since values"]]
                 [status errors])))))))

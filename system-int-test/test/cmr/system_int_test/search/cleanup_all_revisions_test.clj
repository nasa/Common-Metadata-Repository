(ns cmr.system-int-test.search.cleanup-all-revisions-test
  "This tests that when metadata db cleans up old revisions of superseded collections they will no
  longer be found in the all revisions search."
  (:require 
    [clj-http.client :as client]
    [clj-http.client :as client]
    [clojure.test :refer :all]
    [cmr.elastic-utils.config :as elastic-config]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.url-helper :as url-helper]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest cleanup-all-revisions-test
  (let [umm-c (data-umm-c/collection {:EntryTitle "coll1"})
        coll1s (doall (for [n (range 12)]
                        (d/ingest-umm-spec-collection "PROV1" umm-c)))
        coll2s (doall (for [n (range 3)]
                        (d/ingest-umm-spec-collection "PROV2" umm-c)))
        all-collections (concat coll1s coll2s)
        all-collections-after-cleanup (concat (drop 2 coll1s) coll2s)
        all-collections-after-force-delete (concat (drop 2 coll1s) (drop 1 coll2s))]

    (index/wait-until-indexed)
    ;; All collections should be present initially
    (d/assert-refs-match all-collections (search/find-refs :collection {:all-revisions true
                                                                        :page-size 20}))

    (is (= 204 (:status (mdb/cleanup-old-revisions))))
    (index/wait-until-indexed)

    (d/assert-refs-match all-collections-after-cleanup
                         (search/find-refs :collection {:all-revisions true
                                                        :page-size 20}))

    ;; Remove the first coll2 through force delete
    (let [{:keys [concept-id revision-id]} (first coll2s)]
      (is (= 200 (:status (mdb/force-delete-concept concept-id revision-id)))))
    (index/wait-until-indexed)

    (d/assert-refs-match all-collections-after-force-delete
                         (search/find-refs :collection {:all-revisions true
                                                        :page-size 20}))))

;; This test will simulate a failure (and recovery) of a deletion event into the
;; all_collection_revisions index for placing a tombstone where it should be.
(deftest reindex-all-revisions-test
  ;; Generate a collection and ingest it twice.
  (let [umm-c (data-umm-c/collection {:EntryTitle "coll1"})
        coll1s (doall (for [n (range 2)]
                        (d/ingest-umm-spec-collection "PROV1" umm-c)))
        collection-to-delete (first coll1s)]
    (index/wait-until-indexed)
    ;; Make sure the collection was ingested twice.
    (is (d/refs-match? coll1s (search/find-refs :collection {:all-revisions true :page-size 20})))
    ;; Tell metadata db to not send messages to simulate some kind of network or messaging failure
    (dev-sys-util/eval-in-dev-sys
     `(cmr.metadata-db.config/set-publish-messages! false))
    ;; Create a tombstone in metadata-db. Because messages are off this should simulate a failure to
    ;; handle a delete event from elastic, making the two out of sync.
    (ingest/delete-concept (d/item->concept collection-to-delete))
    ;; Re-enable messages to enable normal operation.
    (dev-sys-util/eval-in-dev-sys
     `(cmr.metadata-db.config/set-publish-messages! true))
    ;; Trigger the reindexing endpoint to repair the damage caused by the temporary outage.
    ;; This endpoint should run every night in a job automatically.
    (ingest/reindex-all-collections)
    (index/wait-until-indexed)
    ;; Make sure that we have 3 revisions in all_collection_revisions index and 0 items in the
    ;; collections index.
    (is (= 3 (:hits (search/find-refs :collection {:all-revisions true :page-size 20}))))
    (is (= 0 (:hits (search/find-refs :collection {:all-revisions false :page-size 20}))))))

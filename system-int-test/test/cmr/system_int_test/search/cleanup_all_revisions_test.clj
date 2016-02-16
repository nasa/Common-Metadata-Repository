(ns cmr.system-int-test.search.cleanup-all-revisions-test
  "This tests that when metadata db cleans up old revisions of superseded collections they will no
  longer be found in the all revisions search."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cmr.system-int-test.utils.metadata-db-util :as mdb]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.elastic-utils.config :as elastic-config]
            [clj-http.client :as client]
            [cmr.system-int-test.utils.url-helper :as url-helper]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "PROV2"}))

(deftest cleanup-all-revisions-test
  (let [umm-c (dc/collection {:entry-title "coll1"})
        coll1s (doall (for [n (range 12)]
                             (d/ingest "PROV1" umm-c)))
        coll2s (doall (for [n (range 3)]
                             (d/ingest "PROV2" umm-c)))
        all-collections (concat coll1s coll2s)
        all-collections-after-cleanup (concat (drop 2 coll1s) coll2s)
        all-collections-after-force-delete (concat (drop 2 coll1s) (drop 1 coll2s))]

    (index/wait-until-indexed)
    ;; All collections should be present initially
    (is (d/refs-match? all-collections (search/find-refs :collection {:all-revisions true
                                                                      :page-size 20})))

    (is (= 204 (:status (mdb/cleanup-old-revisions))))
    (index/wait-until-indexed)

    (is (d/refs-match? all-collections-after-cleanup
                       (search/find-refs :collection {:all-revisions true
                                                      :page-size 20})))

    ;; Remove the first coll2 through force delete
    (let [{:keys [concept-id revision-id]} (first coll2s)]
     (is (= 200 (:status (mdb/force-delete-concept concept-id revision-id)))))
    (index/wait-until-indexed)

    (is (d/refs-match? all-collections-after-force-delete
                       (search/find-refs :collection {:all-revisions true
                                                      :page-size 20})))))

(deftest reindex-all-revisions-test
  (let [umm-c (dc/collection {:entry-title "coll1"})
        coll1s (doall (for [n (range 2)]
                        (d/ingest "PROV1" umm-c)))
        collection-to-delete (first coll1s)]
    (index/wait-until-indexed)
    (is (d/refs-match? coll1s (search/find-refs :collection {:all-revisions true :page-size 20})))
    (cmr.metadata-db.config/set-publish-messages! false)
    (ingest/delete-concept (d/item->concept collection-to-delete))
    (cmr.metadata-db.config/set-publish-messages! true)
    (ingest/reindex-all-collections)
    (index/wait-until-indexed)
    (Thread/sleep 1000)
    (is (= 3 (:hits (search/find-refs :collection {:all-revisions true :page-size 20}))))))

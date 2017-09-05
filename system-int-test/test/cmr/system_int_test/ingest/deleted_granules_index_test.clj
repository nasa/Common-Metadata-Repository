(ns cmr.system-int-test.ingest.deleted-granules-index-test
  "When a granule is deleted, a document is indexed in the deleted-granules index.
   This namespace tests that functionality"
  (:require
   [clojure.test :refer :all]
   [cmr.indexer.data.concepts.deleted-granule :as deleted-granule]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn check-index-for-deleted-granule
  "Check elastic search deleted-granules index from related deleted granule entry,
   Returns true if document exists, false if it does not."
  [concept-id]
  (index/doc-present? deleted-granule/deleted-granule-index-name
                      deleted-granule/deleted-granule-type-name
                      concept-id))

(deftest deleted-granules-test
  (testing "Ingest granule, delete, then reingest"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G1-PROV1"}))
          ingest-result (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule)
          ingest-revision-id (:revision-id ingest-result)
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 1 (- delete-revision-id ingest-revision-id)))
      (is (check-index-for-deleted-granule (:concept-id granule)))
      (ingest/ingest-concept granule)
      (index/wait-until-indexed)
      (is (not (check-index-for-deleted-granule (:concept-id granule))))))

  (testing "Ingest granule, delete, delete tombstone"
    (dev-sys-util/eval-in-dev-sys `(concept-service/set-days-to-keep-tombstone! 0))
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept (dg/granule-with-umm-spec-collection collection (:concept-id collection) {:concept-id "G2-PROV1"}))
          ingest-result (ingest/ingest-concept granule)
          delete-result (ingest/delete-concept granule)
          ingest-revision-id (:revision-id ingest-result)
          delete-revision-id (:revision-id delete-result)]
      (index/wait-until-indexed)
      (is (= 1 (- delete-revision-id ingest-revision-id)))
      (is (check-index-for-deleted-granule (:concept-id granule)))
      (mdb-util/cleanup-old-revisions)
      (index/wait-until-indexed)
      (is (not (check-index-for-deleted-granule (:concept-id granule))))
      (dev-sys-util/eval-in-dev-sys `(concept-service/set-days-to-keep-tombstone! 365)))))

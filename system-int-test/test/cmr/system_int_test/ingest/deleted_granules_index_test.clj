(ns cmr.system-int-test.ingest.deleted-granules-index-test
  "When a granule is deleted, a document is indexed in the deleted-granules index.
   This namespace tests that functionality"
  (:require
   [clojure.test :refer :all]
   [clj-time.core :as t]
   [cmr.common.util :refer [are3] :as util]
   [cmr.indexer.data.concepts.deleted-granule :as deleted-granule]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn check-index-for-deleted-granule
  "Check elastic search deleted-granules index from related deleted granule entry,
   Returns true if document exists, false if it does not."
  [concept-id]
  (index/doc-present? deleted-granule/deleted-granule-index-name
                      deleted-granule/deleted-granule-type-name
                      concept-id))

(deftest deleted-granules-test-index
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

(deftest deleted-granules-test-search
  (let [collection-prov1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
        collection-prov2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {}))
        granule1-prov1 (d/item->concept (dg/granule-with-umm-spec-collection collection-prov1 (:concept-id collection-prov1) {:concept-id "G1-PROV1"}))
        granule2-prov1 (d/item->concept (dg/granule-with-umm-spec-collection collection-prov1 (:concept-id collection-prov1) {:concept-id "G2-PROV1"}))
        granule3-prov2 (d/item->concept (dg/granule-with-umm-spec-collection collection-prov2 (:concept-id collection-prov2) {:concept-id "G3-PROV2"}))]
    (ingest/ingest-concept granule1-prov1)
    (ingest/delete-concept granule1-prov1)
    (ingest/ingest-concept granule2-prov1)
    (ingest/delete-concept granule2-prov1)
    (ingest/ingest-concept granule3-prov2)
    (ingest/delete-concept granule3-prov2)
    (index/wait-until-indexed)
    (testing "Search for all after date"
      (are3 [params granules]
        (is (= (map #(get-in % [:fields :concept-id])
                    (get-in (search/find-deleted-granules params) [:hits :hits]))))

        "before 1 year"
        {:revision-date (t/minus- (t/now) (t/days 366))}
        []))))

    ;     "after tomorrow"
    ;     {:revision-date (t/plus- (t/now) (t/days 1))}
    ;     []
    ;
    ;     "after yesterday"
    ;     {:revision-date (t/minus- (t/now) (t/days 1))}
    ;     [granule1-prov1 granule2-prov1 granule3-prov2]))
    ;
    ; (testing "Search for all after date for provider"
    ;   (are3 [params granules]
    ;     (is (= (map #(get-in % [:fields :concept-id])
    ;                 (get-in (search/find-deleted-granules params) [:hits :hits]))
    ;            (map :concept-id granules)))
    ;
    ;     "PROV1"
    ;     {:revision-date (t/minus- (t/now) (t/days 1)) :provider-id "PROV1"}
    ;     [granule1-prov1 granule2-prov1]))
    ; (testing "Search for all after date for each collection")
    ; (testing "Combination search")))

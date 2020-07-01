(ns cmr.system-int-test.ingest.deleted-granules-index-test
  "When a granule is deleted, a document is indexed in the deleted-granules index.
   This namespace tests that functionality"
  (:require
   [cheshire.core :as json]
   [clj-time.core :as t]
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3] :as util]
   [cmr.indexer.data.concepts.deleted-granule :as deleted-granule]
   [cmr.metadata-db.services.concept-service :as concept-service]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb-util]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- check-index-for-deleted-granule
  "Check elastic search deleted-granules index from related deleted granule entry,
   Returns true if document exists, false if it does not."
  [concept-id]
  (index/doc-present? deleted-granule/deleted-granule-index-name
                      deleted-granule/deleted-granule-type-name
                      concept-id))

(defn- find-deleted-granules
  "Calls get-deleted-granules endpoint and returns parsed items from response"
  [params]
  (map #(get % "concept-id")
       (json/parse-string
        (get (search/find-deleted-granules
              params)
             :body))))

(deftest deleted-granules-test-index
  (testing "Ingest granule, delete, then reingest"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {}))
          granule (d/item->concept
                   (dg/granule-with-umm-spec-collection
                    collection (:concept-id collection) {:concept-id "G1-PROV1"}))
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
          granule (d/item->concept
                   (dg/granule-with-umm-spec-collection
                    collection (:concept-id collection) {:concept-id "G2-PROV1"}))
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
  (let [collection-prov1 (d/ingest-umm-spec-collection
                          "PROV1"
                          (data-umm-c/collection {:EntryTitle "entry-title 1"
                                                  :ShortName "S1"}))
        collection-prov1-2 (d/ingest-umm-spec-collection
                            "PROV1"
                            (data-umm-c/collection {:EntryTitle "entry-title 2"
                                                    :ShortName "S2"}))
        collection-prov2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {}))
        granule1-prov1 (d/item->concept
                        (dg/granule-with-umm-spec-collection
                         collection-prov1 (:concept-id collection-prov1) {:concept-id "G1-PROV1"}))
        granule2-prov1 (d/item->concept
                        (dg/granule-with-umm-spec-collection
                         collection-prov1 (:concept-id collection-prov1) {:concept-id "G2-PROV1"}))
        granule3-prov1-2 (d/item->concept
                          (dg/granule-with-umm-spec-collection
                           collection-prov1-2 (:concept-id collection-prov1-2) {:concept-id "G3-PROV1"}))
        granule4-prov2 (d/item->concept
                        (assoc (dg/granule-with-umm-spec-collection
                                 collection-prov2 (:concept-id collection-prov2) {:concept-id "G4-PROV2"})
                               :provider-id "PROV2"))]
    (ingest/ingest-concept granule1-prov1)
    (ingest/delete-concept granule1-prov1)
    (ingest/ingest-concept granule2-prov1)
    (ingest/delete-concept granule2-prov1)
    (ingest/ingest-concept granule3-prov1-2)
    (ingest/delete-concept granule3-prov1-2)
    (ingest/ingest-concept granule4-prov2)
    (ingest/delete-concept granule4-prov2)
    (index/wait-until-indexed)

    (testing "Search for all"
      (are3 [params granules]
        (is (= (set (map :concept-id granules))
               (set (find-deleted-granules params))))

        "after tomorrow"
        {:revision_date (t/plus- (t/now) (t/days 1))}
        []

        "after yesterday"
        {:revision_date (t/minus- (t/now) (t/days 1))}
        [granule1-prov1 granule2-prov1 granule3-prov1-2 granule4-prov2]))

    (testing "Deleted granule search header contains application/json content-type"
      (let [results (search/find-deleted-granules {:revision_date (t/minus- (t/now) (t/days 1))})]
        (is (= "application/json;charset=utf-8"
               (get-in results [:headers :content-type])))))

    (testing "Search for all after date for provider"
      (are3 [params granules]
        (is (= (set (map :concept-id granules))
               (set (find-deleted-granules params))))

        "PROV1"
        {:revision_date (t/minus- (t/now) (t/days 1)) :provider "PROV1"}
        [granule1-prov1 granule2-prov1 granule3-prov1-2]

        "PROV2"
        {:revision_date (t/minus- (t/now) (t/days 1)) :provider "PROV2"}
        [granule4-prov2]))

    (testing "Search for all after date for parent-collection-id"
      (are3 [params granules]
        (is (= (set (map :concept-id granules))
               (set (find-deleted-granules params))))

        "in PROV1, parent collection 1"
        {:revision_date (t/minus- (t/now) (t/days 1))
         :parent_collection_id (:concept-id collection-prov1)}
        [granule1-prov1 granule2-prov1]

        "in PROV1, parent collection 2"
        {:revision_date (t/minus- (t/now) (t/days 1))
         :parent_collection_id (:concept-id collection-prov1-2)}
        [granule3-prov1-2]

        "in PROV2, parent collection 1"
        {:revision_date (t/minus- (t/now) (t/days 1))
         :parent_collection_id (:concept-id collection-prov2)}
        [granule4-prov2]))))

(deftest deleted-granule-parameter-validation
  (are3 [errors params]
    (let [response (search/get-search-failure-data
                    (search/find-deleted-granules params))]
      (is (= (:status response) 400))
      (is (= errors (:errors response))))

    "Revision date range validation"
    ["Revision date must be within one year of today."]
    {:revision_date (t/minus- (t/now) (t/days 366))}


    "Unrecognized paramter validation"
    ["Parameter [not_a_valid_parameter] was not recognized."]
    {:revision_date (t/minus- (t/now) (t/days 1)) :Not-a-valid-parameter "test"}


    "Revision date is required"
    ["One revision date is required for deleted granules search."]
    {})

  (testing "Invalid result format"
    (let [response (search/get-search-failure-xml-data
                    (search/find-deleted-granules {:revision_date (t/minus- (t/now) (t/days 1))} :xml))]
      (is (= (:status response) 400))
      (is (= ["Result format [xml] is not supported by deleted granules search. The only format that is supported is json"]
             (:errors response))))))

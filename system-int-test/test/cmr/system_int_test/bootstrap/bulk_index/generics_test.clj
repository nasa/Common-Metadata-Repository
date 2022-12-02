(ns cmr.system-int-test.bootstrap.bulk-index.generics-test
  "Integration test for CMR bulk index generic document operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as data-collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.search.generic-association-test :as association-test]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as association-util]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.generic-util :as generic]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.service-util :as service-util]
   [cmr.system-int-test.utils.tool-util :as tool-util]
   [cmr.system-int-test.utils.tool-util :as tool]
   [cmr.system-int-test.utils.variable-util :as variable-util]
   [cmr.umm-spec.versioning :as umm-version]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"
                                             "provguid2" "PROV2"
                                             "provguid3" "PROV3"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

(deftest ^:oracle bulk-index-grids-for-provider
  (testing "Bulk index grids for a single provider"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed
     (core/disable-automatic-indexing)
     ;; The following is saved, but not indexed due to the above call
     (let [grid1 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1" :grid generic/grid-good :post)
           ;; create a grid on a different provider PROV2
           ;; and this grid won't be indexed as a result of indexing grids of PROV1
           grid2 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-1" :grid generic/grid-good :post)
           {:keys [status errors]} (bootstrap/bulk-index-grids "PROV1" nil)]

       ;; The above bulk-index-grids call with nil headers has no token
       (is (= [401 ["You do not have permission to perform that action."]]
              [status errors]))
       (is (= 0 (:hits (search/find-refs :grid {}))))

       ;; The following bulk-index-grids call uses system token.
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)

       (testing "Grid concepts are indexed."
         ;; Note: only grid1 is indexed, grid2 is not.
         (let [{:keys [hits refs]} (search/find-refs :grid {})]
           (is (= 1 hits))
           (is (= (:concept-id grid1)
                  (:id (first refs))))))

       (testing "Bulk index multiple grids for a single provider"
         ;; Ingest three more grids
         (let [grid3 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-2" :grid generic/grid-good :post)
               grid4 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-3" :grid generic/grid-good :post)
               grid5 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-4" :grid generic/grid-good :post)]

          ;; The above three new grids are not indexed, only grid1 is indexed.
          (is (= 1 (:hits (search/find-refs :grid {}))))

          ;; bulk index again, all the grids in PROV1 should be indexed.
          (bootstrap/bulk-index-grids "PROV1")
          (index/wait-until-indexed)

          (let [{:keys [hits refs]} (search/find-refs :grid {})]
            (is (= 4 hits))
            (is (= 4 (count refs)))
            (is (= (set (map :id refs))
                   (set (map :concept-id [grid1, grid3, grid4, grid5]))))))))

     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-all-grids
  (testing "Bulk index grids for multiple providers, implicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (let [grid-prov1 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1" :grid generic/grid-good :post)
           grid-prov2 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2" :grid generic/grid-good :post)
           grid-prov3 (generic/ingest-generic-document nil "PROV3" "Test-Native-Id-3" :grid generic/grid-good :post)]

      (is (= 0 (:hits (search/find-refs :grid {}))))

      (bootstrap/bulk-index-grids)
      (index/wait-until-indexed)

      (testing "Grid concepts are indexed."
        (let [{:keys [hits refs]} (search/find-refs :grid {})]
          (is (= 3 hits))
          (is (= 3 (count refs)))
          (is (= (set (map :id refs))
                 (set (map :concept-id [grid-prov1, grid-prov2, grid-prov3])))))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-grid-revisions
  (testing "Bulk index grids index all revisions index as well"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (echo-util/login (system/context) "user1")
           ;; Create, then delete, then re-instate a grid for PROV1
           grid1-1 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1"
                                                    :grid generic/grid-good :post)
           grid1-2-tombstone (merge (generic/ingest-generic-document token "PROV1" "Test-Native-Id-1"
                                                                     :grid generic/grid-good :delete)
                                    generic/grid-good
                                    {:deleted true
                                     :user-id "user1"})
           grid1-3 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1"
                                                    :grid generic/grid-good :put)

           ;; Create, then update, then delete a grid for PROV2
           grid2-1 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2"
                                                    :grid generic/grid-good :post)
           grid2-2 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2"
                                                    :grid generic/grid-good :put)
           grid2-3-tombstone (merge (generic/ingest-generic-document token "PROV2" "Test-Native-Id-2"
                                                                     :grid generic/grid-good :delete)
                                    generic/grid-good
                                    {:deleted true
                                     :user-id "user1"})

           ;; Create a grid for PROV3
           grid3 (generic/ingest-generic-document nil "PROV3" "Test-Native-Id-3"
                                                  :grid generic/grid-good :post)]

       (testing "Before bulk indexing, search for grids found nothing"
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {:all-revisions true})]
           (is (= 200 status))
           (is (= 0 (:hits results)))))

       ;; Just index PROV1
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)

       (testing "After bulk indexing a provider, search found all grid revisions for that provider"
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {:all-revisions true})
               results-tuples (map
                               #(hash-map :concept-id (get-in % [:meta :concept-id])
                                          :revision-id (get-in % [:meta :revision-id]))
                               (:items results))
               original-tuples (map
                                #(hash-map :concept-id (:concept-id %)
                                           :revision-id (:revision-id %))
                                [grid1-1, grid1-2-tombstone, grid1-3])]

           (is (= 200 status))
           (is (= 3 (:hits results)))))

       ;; Now index all grids
       (bootstrap/bulk-index-grids)
       (index/wait-until-indexed)

       (testing "After bulk indexing all grids, search for grids finds all revisions of all providers"
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {:all-revisions true})
               results-tuples (map
                               #(hash-map :concept-id (get-in % [:meta :concept-id])
                                          :revision-id (get-in % [:meta :revision-id]))
                               (:items results))
               original-tuples (map
                                #(hash-map :concept-id (:concept-id %)
                                           :revision-id (:revision-id %))
                                [grid1-1, grid1-2-tombstone, grid1-3,
                                 grid2-1, grid2-2, grid2-3-tombstone, grid3])]
           (is (= 200 status))
           (is (= 7 (:hits results)))
           (is (= (set results-tuples)
                  (set original-tuples)))))

       (testing "The regular search does not include the deleted grids"
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {})
               results-tuples (map
                               #(hash-map :concept-id (get-in % [:meta :concept-id])
                                          :revision-id (get-in % [:meta :revision-id]))
                               (:items results))
               original-tuples (map
                                #(hash-map :concept-id (:concept-id %)
                                           :revision-id (:revision-id %))
                                [grid1-3, grid3])]
           (is (= 200 status))
           (is (= 2 (:hits results)))
           (is (= (set results-tuples)
                  (set original-tuples)))))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

(deftest ^:oracle bulk-index-generics-association
  (testing "Bulk index two grids which have been associated together and contain association details"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
      (core/disable-automatic-indexing)
      ;; Create two grids
      (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           grid2 (generic/ingest-generic-document nil "PROV1" "tg2" :grid generic/grid-good :post)
           grid1-concept-id (:concept-id grid1)
           grid2-concept-id (:concept-id grid2)
           grid1-revision-id (:revision-id grid1)
           grid2-revision-id (:revision-id grid2)]
       ;; No index since they are disabled
       (is (= 0 (:hits (search/find-refs :grid {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Grids should now be indexed
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; Associate the two grids
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data "some data" :revision-id grid2-revision-id}])
             _ (index/wait-until-indexed)
             grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)])
       ;; Associations in the index should be empty since they were not there when the record was re-indexed
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Should still have two indexes for the grids
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; The association should still be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is (= (set [{:data "some data" :concept-id grid2-concept-id :revision-id grid2-revision-id}])
                (set (:association-details grid-search-result2))))))
    ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-association-json-endpoint
  (testing "Bulk index two grids which have been associated together and contain association details search on json endpoint"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
     (core/disable-automatic-indexing)
     ;; Create two grids
     (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           grid2 (generic/ingest-generic-document nil "PROV1" "tg2" :grid generic/grid-good :post)
           grid1-concept-id (:concept-id grid1)
           grid2-concept-id (:concept-id grid2)
           grid1-revision-id (:revision-id grid1)
           grid2-revision-id (:revision-id grid2)]
       ;; No index since they are disabled
       (is (= 0 (:hits (search/find-refs :grid {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Grids should now be indexed
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; Associate the two grids
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data "some data" :revision-id grid2-revision-id}])
             _ (index/wait-until-indexed)
             ;; Search the grid for associations
             grid-search-result (association-test/get-associations-and-details "grids.json" "native_id=tg1" :grids false)])
       ;; Associations in the index should be empty since they were not there when the record was re-indexed
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.json" "native_id=tg1" :grids false)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Should still have two indexes for the grids
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; The association should still be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.json" "native_id=tg1" :grids false)]
         (is (= (set [{:data "some data", :concept-id grid2-concept-id :revision-id grid2-revision-id}])
                (set (:association-details grid-search-result2))))))
     ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-multiple-associations
  (testing "Bulk index three grids multiple grids ensure associaions are returned"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
     (core/disable-automatic-indexing)
     ;; Create two grids
     (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           grid2 (generic/ingest-generic-document nil "PROV1" "tg2" :grid generic/grid-good :post)
           grid3 (generic/ingest-generic-document nil "PROV1" "tg3" :grid generic/grid-good :post)
           grid1-concept-id (:concept-id grid1)
           grid2-concept-id (:concept-id grid2)
           grid3-concept-id (:concept-id grid3)
           grid1-revision-id (:revision-id grid1)
           grid2-revision-id (:revision-id grid2)
           grid3-revision-id (:revision-id grid3)]
       ;; No indicies since they are disabled
       (is (= 0 (:hits (search/find-refs :grid {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; three Grids should now be indexed
       (is (= 3 (:hits (search/find-refs :grid {}))))
       ;; Associate the grids
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data "some data" :revision-id grid2-revision-id}, {:concept-id grid3-concept-id :data "some other data" :revision-id grid3-revision-id}])
             _ (index/wait-until-indexed)
             grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)])
       ;; Associations in the index should be empty since they were not there when the record was re-indexed
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Should still have three indexes for the grids
       (is (= 3 (:hits (search/find-refs :grid {}))))
       ;; The association should still be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is (= (set [{:data "some data", :concept-id grid2-concept-id :revision-id grid2-revision-id}, {:data "some other data", :concept-id grid3-concept-id :revision-id grid3-revision-id}])
                (set (:association-details grid-search-result2))))))
     ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-specific-revision-id-association
  (testing "Bulk index two grids which have been associated together and contain association details between specific revisions"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
     (core/disable-automatic-indexing)
     ;; Create two grids
     (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           grid2 (generic/ingest-generic-document nil "PROV1" "tg2" :grid generic/grid-good :post)
           updated-grid-2 (generic/ingest-generic-document nil "PROV1" "tg2" :grid generic/grid-good :post)
           grid1-concept-id (:concept-id grid1)
           grid2-concept-id (:concept-id grid2)
           grid1-revision-id (:revision-id grid1)
           updated-grid-2-revision-id (:revision-id updated-grid-2)]
       ;; No index since they are disabled
       (is (= 2 updated-grid-2-revision-id))
       (is (= 0 (:hits (search/find-refs :grid {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Grids should now be indexed
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; Associate the two grids
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data "some data" :revision-id 1}])
             _ (index/wait-until-indexed)
             grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)])
       ;; Associations in the index should be empty since they were not there when the record was re-indexed
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids "PROV1")
       (index/wait-until-indexed)
       ;; Should still have two indexes for the grids
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; The association should still be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is (= (set [{:data "some data", :concept-id grid2-concept-id :revision-id 1}])
                (set (:association-details grid-search-result2))))))
     ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-different-concept-types
  (testing "bulk index two generic concepts which have been associated together and contain association details"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
     (core/disable-automatic-indexing)
     ;; Create a grid and an order-option
     (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           orderOption1 (generic/ingest-generic-document nil "PROV1" "oo1" :order-option generic/order-option :post)
           grid1-concept-id (:concept-id grid1)
           orderOption1-concept-id (:concept-id orderOption1)
           grid1-revision-id (:revision-id grid1)
           orderOption1-revision-id (:revision-id orderOption1)]
       ;; No index since they are disabled
       (is (= 0 (:hits (search/find-refs :grid {}))))
       (is (= 0 (:hits (search/find-refs :order-option {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids "PROV1")
       (bootstrap/bulk-index-generics :order-option)
       (index/wait-until-indexed)
       ;; Grid and order-option should now be indexed
       (is (= 1 (:hits (search/find-refs :grid {}))))
       (is (= 1 (:hits (search/find-refs :order-option {}))))
       ;; Associate the the grid and the order-option
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id orderOption1-concept-id :data "some data" :revision-id orderOption1-revision-id}])
             _ (index/wait-until-indexed)
             grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :order-options true)]
             (is (= 200 (:status response1))))
       ;; Associations in the index should be empty since they were not there when the record was re-indexed
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :order-options true)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids)
       (bootstrap/bulk-index-generics :order-option "PROV1")
       (index/wait-until-indexed)
       ;; Should still have two indexes for both of the concepts
       (is (= 1 (:hits (search/find-refs :grid {}))))
       (is (= 1 (:hits (search/find-refs :order-option {}))))
       ;; The association should still be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :order-options true) order-option-search (association-test/get-associations-and-details "order-options.umm_json" "native_id=oo1" :grids true)]
         (is (= (set [{:data "some data", :concept-id orderOption1-concept-id :revision-id orderOption1-revision-id}])
                (set (:association-details grid-search-result2))))
         (is (= (set [{:data "some data", :concept-id grid1-concept-id :revision-id grid1-revision-id}])
                (set (:association-details order-option-search))))))
      ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-different-providers
  (testing "bulk index two grids which have been associated together and contain association details but, belong to different providers"
    (system/only-with-real-database
      ;; Disable message publishing so items are not indexed but, they are ingested into the system.
     (core/disable-automatic-indexing)
     ;; Create two grids
     (let [token (echo-util/login (system/context) "user1")
           grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
           grid2 (generic/ingest-generic-document nil "PROV2" "tg2" :grid generic/grid-good :post)
           grid1-concept-id (:concept-id grid1)
           grid2-concept-id (:concept-id grid2)
           grid1-revision-id (:revision-id grid1)
           grid2-revision-id (:revision-id grid2)]
       ;; No index since they are disabled
       (is (= 0 (:hits (search/find-refs :grid {}))))
       ;; bulk index to create the indexes for the ingested grids
       (bootstrap/bulk-index-grids)
       (index/wait-until-indexed)
       ;; Grids should now be indexed
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; Associate the two grids
       (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                        token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :revision-id grid2-revision-id}])
             _ (index/wait-until-indexed)
             grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is (= 200 (:status response1))))
       (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is empty? (set (:association-details grid-search-pre-bulk))))
       ;; re-index the association since it was not there at the start of the test
       (bootstrap/bulk-index-grids)
       (index/wait-until-indexed)
       ;; Should still have two indexes for the grids
       (is (= 2 (:hits (search/find-refs :grid {}))))
       ;; The association should now be indexed
       (let [grid-search-result2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
         (is (= (set [{:data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :concept-id grid2-concept-id :revision-id grid2-revision-id}])
                (set (:association-details grid-search-result2))))))
     ;; Reenable message publishing
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-generics-different-providers-reindex-only-one
 (testing "Bulk index two grids reindex only one them"
   (system/only-with-real-database
     ;; Disable message publishing so items are not indexed but, they are ingested into the system.
    (core/disable-automatic-indexing)
    ;; Create two grids of different providers
    (let [token (echo-util/login (system/context) "user1")
          grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
          grid2 (generic/ingest-generic-document nil "PROV2" "tg2" :grid generic/grid-good :post)
          grid1-concept-id (:concept-id grid1)
          grid2-concept-id (:concept-id grid2)
          grid1-revision-id (:revision-id grid1)
          grid2-revision-id (:revision-id grid2)]
      ;; No index since they are disabled
      (is (= 0 (:hits (search/find-refs :grid {}))))
      ;; bulk index to create the indexes for the ingested grids
      (bootstrap/bulk-index-grids)
      (index/wait-until-indexed)
      ;; Grids should now be indexed
      (is (= 2 (:hits (search/find-refs :grid {}))))
      ;; Associate the two grids
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :revision-id grid2-revision-id}])
            _ (index/wait-until-indexed)
            grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)])
      ;; Associations in the index should be empty since they were not there when the record was re-indexed
      (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true)]
        (is empty? (set (:association-details grid-search-pre-bulk))))
      ;; re-index the association since it was not there at the start of the test
      (bootstrap/bulk-index-grids "PROV2")
      (index/wait-until-indexed)
      ;; Should still have two indexes for the grids
      (is (= 2 (:hits (search/find-refs :grid {}))))
      ;; The association should be empty since it was not reindexed the PROV1 grid, but, since PROV2 was reindexed it should contain an assocaiation record
      (let [grid-search-result-prov1 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :grids true) grid-search-result-prov2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg2" :grids true)]
        (is (= (set {})
               (set (:association-details grid-search-result-prov1))))
        (is (= (set [{:data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :concept-id grid1-concept-id :revision-id grid1-revision-id}])
               (set (:association-details grid-search-result-prov2)))))))
    ;; Reenable message publishing
    (core/reenable-automatic-indexing)))

(deftest ^:oracle bulk-index-grids-service-association
 (testing "Bulk index a grid associated to a service verify association on the grid"
   (system/only-with-real-database
     ;; Disable message publishing so items are not indexed but, they are ingested into the system.
    (core/disable-automatic-indexing)
    ;; Create two grids and a service
    (let [token (echo-util/login (system/context) "user1")
          grid1 (generic/ingest-generic-document nil "PROV1" "tg1" :grid generic/grid-good :post)
          grid2 (generic/ingest-generic-document nil "PROV2" "tg2" :grid generic/grid-good :post)
          grid1-concept-id (:concept-id grid1)
          grid2-concept-id (:concept-id grid2)
          grid1-revision-id (:revision-id grid1)
          grid2-revision-id (:revision-id grid2)
          sv1 (service-util/ingest-service-with-attrs {:native-id "sv1"
                                                       :Name "service1"})
          sv1-concept-id (:concept-id sv1)
          sv1-revision-id (:revision-id sv1)]
      ;; No index since they are disabled
      (is (= 0 (:hits (search/find-refs :grid {}))))
      (is (= 0 (:hits (search/find-refs :service {}))))
      ;; bulk index to create the indexes for the ingested grids and the service
      (bootstrap/bulk-index-grids)
      (bootstrap/bulk-index-services)
      (index/wait-until-indexed)
      ;; the Grids and the service should now be indexed
      (is (= 2 (:hits (search/find-refs :grid {}))))
      (is (= 1 (:hits (search/find-refs :service {}))))
      ;; Associate the two grids and associate the service to one of the grids
      (let [response1 (association-util/generic-associate-by-concept-ids-revision-ids
                       token grid1-concept-id grid1-revision-id [{:concept-id grid2-concept-id :data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :revision-id grid2-revision-id}])
            serviceAssociation  (association-util/generic-associate-by-concept-ids-revision-ids token sv1-concept-id sv1-revision-id [{:concept-id grid1-concept-id :data "Some data" :revision-id grid1-revision-id}])
            _ (index/wait-until-indexed)
            grid-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :services true)
            service-search-result (association-test/get-associations-and-details "services.umm_json" "native_id=sv1" :grids true)]
            (is (= 200 (:status serviceAssociation))))
      ;; Associations in the index should be empty since they were not there when the record was re-indexed
      (let [grid-search-pre-bulk (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :services true)]
        (is empty? (set (:association-details grid-search-pre-bulk))))
      ;; re-index the association since it was not there at the start of the test
      (bootstrap/bulk-index-grids)
      (bootstrap/bulk-index-services)
      (index/wait-until-indexed)
      ;; Should still have two indexes for the grids and one for the service
      (is (= 2 (:hits (search/find-refs :grid {}))))
      (is (= 1 (:hits (search/find-refs :service {}))))
      ;; The association should exist for both the service-grid ns well as the grid-grids
      (let [grid-service-assocaition-search-result (association-test/get-associations-and-details "grids.umm_json" "native_id=tg1" :services true) grid-search-result-prov2 (association-test/get-associations-and-details "grids.umm_json" "native_id=tg2" :grids true)]
        (is (= (set [{:concept-id sv1-concept-id :data "Some data" :revision-id sv1-revision-id}])
               (set (:association-details grid-service-assocaition-search-result))))
        (is (= (set [{:data {:convert-format {:XYZ "ZYX"} :allow-regridding "true"} :concept-id grid1-concept-id :revision-id grid1-revision-id}])
               (set (:association-details grid-search-result-prov2)))))))
    ;; Reenable message publishing
    (core/reenable-automatic-indexing)))

(ns cmr.system-int-test.bootstrap.bulk-index.generics-test
  "Integration test for CMR bulk index generic document operations."
  (:require
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as data-collection]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-json :as data-umm-json]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.association-util :as assoc-util]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.generic-util :as generic]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tool-util :as tool]
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
     ;; Disable message publishing so items are not indexed.
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
         (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-2" :grid generic/grid-good :post)
         (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-3" :grid generic/grid-good :post)
         (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-4" :grid generic/grid-good :post)

         ;; The above three new grids are not indexed, only grid1 is indexed. 
         (is (= 1 (:hits (search/find-refs :grid {}))))

         ;; bulk index again, all the grids in PROV1 should be indexed. 
         (bootstrap/bulk-index-grids "PROV1")
         (index/wait-until-indexed)

         (let [{:keys [hits refs]} (search/find-refs :grid {})]
           (is (= 4 hits))
           (is (= 4 (count refs))))))
     
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-grids
  (testing "Bulk index grids for multiple providers, explicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-2" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-3" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-4" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV3" "Test-Native-Id-5" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV3" "Test-Native-Id-6" :grid generic/grid-good :post)

     (is (= 0 (:hits (search/find-refs :grid {}))))

     (bootstrap/bulk-index-grids "PROV1")
     (bootstrap/bulk-index-grids "PROV2")
     (bootstrap/bulk-index-grids "PROV3")
     (index/wait-until-indexed)

     (testing "Grid concepts are indexed."
       (let [{:keys [hits refs]} (search/find-refs :grid {})]
         (is (= 6 hits))
         (is (= 6 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-all-grids
  (testing "Bulk index grids for multiple providers, implicitly"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     ;; The following are saved, but not indexed due to the above call
     (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2" :grid generic/grid-good :post)
     (generic/ingest-generic-document nil "PROV3" "Test-Native-Id-3" :grid generic/grid-good :post)

     (is (= 0 (:hits (search/find-refs :grid {}))))

     (bootstrap/bulk-index-grids)
     (index/wait-until-indexed)
     
     (testing "Grid concepts are indexed." 
       (let [{:keys [hits refs]} (search/find-refs :grid {})] 
         (is (= 3 hits)) 
         (is (= 3 (count refs)))))
     ;; Re-enable message publishing.
     (core/reenable-automatic-indexing))))

(deftest ^:oracle bulk-index-grid-revisions
  (testing "Bulk index grids index all revisions index as well"
    (system/only-with-real-database
     ;; Disable message publishing so items are not indexed.
     (core/disable-automatic-indexing)
     (let [token (echo-util/login (system/context) "user1")
           grid1-1 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1"
                                                    :grid generic/grid-good :post)
           grid1-2-tombstone (merge (generic/ingest-generic-document token "PROV1" "Test-Native-Id-1"
                                                                     :grid generic/grid-good :delete)
                                    generic/grid-good
                                    {:deleted true
                                     :user-id "user1"})
           grid1-3 (generic/ingest-generic-document nil "PROV1" "Test-Native-Id-1"
                                                    :grid generic/grid-good :put)
           
           grid2-1 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2"
                                                    :grid generic/grid-good :post)
           grid2-2 (generic/ingest-generic-document nil "PROV2" "Test-Native-Id-2"
                                                    :grid generic/grid-good :put)
           grid2-3-tombstone (merge (generic/ingest-generic-document token "PROV2" "Test-Native-Id-2"
                                                                     :grid generic/grid-good :delete)
                                    generic/grid-good
                                    {:deleted true
                                     :user-id "user1"})
           
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
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {:all-revisions true})]
           (is (= 200 status))
           (is (= 3 (:hits results)))))

       ;; Now index all grids
       (bootstrap/bulk-index-grids)
       (index/wait-until-indexed)
       
       (testing "After bulk indexing all grids, search for grids finds all revisions of all providers" 
         (let [{:keys [status results]} (search/find-concepts-umm-json :grid {:all-revisions true})] 
           (is (= 200 status)) 
           (is (= 7 (:hits results)))))

       ;; Re-enable message publishing.
       (core/reenable-automatic-indexing)))))

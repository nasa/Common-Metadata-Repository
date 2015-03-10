(ns cmr.system-int-test.bootstrap.bulk-index-test
  "Integration test for CMR bulk indexing."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [clj-time.core :as t]
            [cmr.system-int-test.utils.test-environment :as test-env]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; This test runs bulk index with some concepts in mdb that are good, and some that are
;; deleted, and some that have not yet been deleted, but have an expired deletion date.
(deftest bulk-index-with-some-deleted
  (test-env/only-with-real-database
    (let [;; saved but not indexed
          umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (ingest/save-concept {:concept-type :collection
                                      :format "application/echo10+xml"
                                      :metadata xml1
                                      :extra-fields {:short-name "coll1"
                                                     :entry-title "coll1"
                                                     :entry-id "coll1"
                                                     :version-id "v1"}
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :short-name "coll1"})
          umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))
          ;; saved and indexed by ingest
          coll2 (d/ingest "PROV1" (dc/collection {:short-name "coll2" :entry-title "coll2"}))
          coll2-tombstone {:concept-id (:concept-id coll2)
                           :revision-id (inc (:revision-id coll2))}
          ;; saved (with old delete-time), but not indexed
          umm3 (dc/collection {:short-name "coll3" :entry-title "coll3" :delete-time "2000-01-01T12:00:00Z"})
          xml3 (echo10/umm->echo10-xml umm3)
          coll3 (ingest/save-concept {:concept-type :collection
                                      :format "application/echo10+xml"
                                      :metadata xml3
                                      :extra-fields {:short-name "coll3"
                                                     :entry-title "coll3"
                                                     :entry-id "coll1"
                                                     :version-id "v1"
                                                     :delete-time "2000-01-01T12:00:00Z"}
                                      :provider-id "PROV1"
                                      :native-id "coll3"
                                      :short-name "coll3"})
          ;; a granule saved with a nil delete time but an expired delete time in the xml
          ummg1 (dg/granule coll1 {:granule-ur "gran1" :delete-time "2000-01-01T12:00:00Z"})
          xmlg1 (echo10/umm->echo10-xml ummg1)
          gran1 (ingest/save-concept {:concept-type :granule
                                      :provider-id "PROV1"
                                      :native-id "gran1"
                                      :format "application/echo10+xml"
                                      :metadata xmlg1
                                      :extra-fields {:parent-collection-id (:concept-id umm1)}})]
      (ingest/tombstone-concept coll2-tombstone)

      (bootstrap/bulk-index-provider "PROV1")
      (index/wait-until-indexed)

      (testing "Expired documents are not indexed during bulk indexing and deleted documents
               get deleted."
               (are [search concept-type expected]
                    (d/refs-match? expected (search/find-refs concept-type search))
                    {:concept-id (:concept-id coll1)} :collection [umm1]
                    {:concept-id (:concept-id coll2)} :collection []
                    {:concept-id (:concept-id umm1)} :granule [])))))

;; This test verifies that the bulk indexer can run concurrently with ingest and indexing of items.
;; This test performs the following steps:
;; 1. Saves ten collections in metadata db.
;; 2. Saves three granules for each of those collections in metadata db.
;; 3. Ingests ten granules five times each in a separate thread.
;; 4. Concurrently executes a bulk index operation for the provider.
;; 5. Waits for the bulk indexing and granule ingest to complete.
;; 6. Searches for all of the saved/ingested concepts by concept-id.
;; 7. Verifies that the concepts returned by search have the expected revision ids.

(deftest bulk-index-after-ingest
  (test-env/only-with-real-database
    (let [collections (for [x (range 1 11)]
                        (let [umm (dc/collection {:short-name (str "short-name" x)
                                                  :entry-title (str "title" x)})
                              xml (echo10/umm->echo10-xml umm)
                              concept-map {:concept-type :collection
                                           :format "application/echo10+xml"
                                           :metadata xml
                                           :extra-fields {:short-name (str "short-name" x)
                                                          :entry-title (str "title" x)
                                                          :version-id "v1"}
                                           :provider-id "PROV1"
                                           :native-id (str "coll" x)
                                           :short-name (str "short-name" x)}
                              {:keys [concept-id revision-id]} (ingest/save-concept concept-map)]
                          (assoc umm :concept-id concept-id :revision-id revision-id)))
          granules1 (mapcat (fn [collection]
                              (doall
                                (for [x (range 1 4)]
                                  (let [pid (:concept-id collection)
                                        umm (dg/granule collection)
                                        xml (echo10/umm->echo10-xml umm)
                                        concept-map {:concept-type :granule
                                                     :provider-id "PROV1"
                                                     :native-id (str "gran-" pid "-" x)
                                                     :extra-fields {:parent-collection-id pid}
                                                     :format "application/echo10+xml"
                                                     :metadata xml}
                                        {:keys [concept-id revision-id]} (ingest/save-concept concept-map)]
                                    (assoc umm :concept-id concept-id :revision-id revision-id)))))
                            collections)
          ;; granules2 and f (the future) are used to ingest ten granules five times each in
          ;; a separate thread to verify that bulk indexing with concurrent ingest does the right
          ;; thing.
          granules2 (let [collection (first collections)
                          pid (:concept-id collection)]
                      (for [x (range 1 11)]
                        (dg/granule collection {:granule-ur (str "gran2-" pid "-" x)})))
          f (future (dotimes [n 5]
                      (doall (map (fn [gran]
                                    (Thread/sleep 100)
                                    (d/ingest "PROV1" gran))
                                  granules2))))]

      (bootstrap/bulk-index-provider "PROV1")
      ;; force our future to complete
      @f
      (index/wait-until-indexed)

      (testing "retrieval after bulk indexing returns the latest revision."
        (doseq [collection collections]
          (let [{:keys [concept-id revision-id]} collection
                response (search/find-refs :collection {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            ;; the latest revision should be indexed
            (is (= es-revision-id revision-id))))
        (doseq [granule granules1]
          (let [{:keys [concept-id revision-id]} granule
                response (search/find-refs :granule {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            (is (= es-revision-id revision-id))))
        (doseq [granule (last @f)]
          (let [{:keys [concept-id]} granule
                response (search/find-refs :granule {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            (is (= 5 es-revision-id))))))))

(deftest invalid-provider-bulk-index-validation-test
  (test-env/only-with-real-database
    (testing "Validation of a provider supplied in a bulk-index request."
      (let [{:keys [status errors]} (bootstrap/bulk-index-provider "NCD4580")]
        (is (= [400 ["Provider: [NCD4580] does not exist in the system"]]
               [status errors]))))))

(deftest collection-bulk-index-validation-test
  (test-env/only-with-real-database
    (let [umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (ingest/save-concept {:concept-type :collection
                                      :format "application/echo10+xml"
                                      :metadata xml1
                                      :extra-fields {:short-name "coll1"
                                                     :entry-title "coll1"
                                                     :entry-id "coll1"
                                                     :version-id "v1"}
                                      :provider-id "PROV1"
                                      :native-id "coll1"
                                      :short-name "coll1"})
          umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))
          ummg1 (dg/granule coll1 {:granule-ur "gran1"})
          xmlg1 (echo10/umm->echo10-xml ummg1)
          gran1 (ingest/save-concept {:concept-type :granule
                                      :provider-id "PROV1"
                                      :native-id "gran1"
                                      :format "application/echo10+xml"
                                      :metadata xmlg1
                                      :extra-fields {:parent-collection-id (:concept-id umm1)}})
          valid-prov-id "PROV1"
          valid-coll-id (:concept-id umm1)
          invalid-prov-id "NCD4580"
          invalid-coll-id "C12-PROV1"
          err-msg1 (format "Provider: [%s] does not exist in the system" invalid-prov-id)
          err-msg2 (format "Collection [%s] does not exist." invalid-coll-id)
          {:keys [status errors] :as succ-stat} (bootstrap/bulk-index-collection
                                                  valid-prov-id valid-coll-id)
          ;; invalid provider and collection
          {:keys [status errors] :as fail-stat1} (bootstrap/bulk-index-collection
                                                   invalid-prov-id invalid-coll-id)
          ;; valid provider and invalid collection
          {:keys [status errors] :as fail-stat2} (bootstrap/bulk-index-collection
                                                   valid-prov-id invalid-coll-id)
          ;; invalid provider and valid collection
          {:keys [status errors] :as fail-stat3} (bootstrap/bulk-index-collection
                                                   invalid-prov-id valid-coll-id)]

      (testing "Validation of a collection supplied in a bulk-index request."
        (are [expected actual] (= expected actual)
             [202 nil] [(:status succ-stat) (:errors succ-stat)]
             [400 [err-msg1]] [(:status fail-stat1) (:errors fail-stat1)]
             [400 [err-msg2]] [(:status fail-stat2) (:errors fail-stat2)]
             [400 [err-msg1]] [(:status fail-stat3) (:errors fail-stat3)])))))


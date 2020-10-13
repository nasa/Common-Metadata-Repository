(ns cmr.system-int-test.bootstrap.bulk-index.concurrent-test
  "Integration tests for CMR bulk index operations requiring concurrency."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.echo10.echo10-core :as echo10]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})]))

;; This test verifies that the bulk indexer can run concurrently with ingest and indexing of items.
;; This test performs the following steps:
;; 1. Saves ten collections in metadata db.
;; 2. Saves three granules for each of those collections in metadata db.
;; 3. Ingests ten granules five times each in a separate thread.
;; 4. Concurrently executes a bulk index operation for the provider.
;; 5. Waits for the bulk indexing and granule ingest to complete.
;; 6. Searches for all of the saved/ingested concepts by concept-id.
;; 7. Verifies that the concepts returned by search have the expected revision ids.

(deftest ^:oracle bulk-index-after-ingest
  (s/only-with-real-database
    (let [collections (for [x (range 1 11)]
                        (let [umm (dc/collection {:short-name (str "short-name" x)
                                                  :entry-title (str "title" x)})
                              xml (echo10/umm->echo10-xml umm)
                              concept-map {:concept-type :collection
                                           :format "application/echo10+xml"
                                           :metadata xml
                                           :extra-fields {:short-name (str "short-name" x)
                                                          :entry-title (str "title" x)
                                                          :entry-id (str "entry-id" x)
                                                          :version-id "v1"}
                                           :provider-id "PROV1"
                                           :native-id (str "coll" x)
                                           :short-name (str "short-name" x)}
                              {:keys [concept-id revision-id]} (mdb/save-concept concept-map)]
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
                                                     :extra-fields {:parent-collection-id pid
                                                                    :parent-entry-title
                                                                    (:entry-title collection)
                                                                    :granule-ur (str "gran-" pid "-" x)}
                                                     :format "application/echo10+xml"
                                                     :metadata xml}
                                        {:keys [concept-id revision-id]} (mdb/save-concept concept-map)]
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
            (is (= es-revision-id revision-id) (str "Failure for granule " concept-id))))
        (doseq [granule (last @f)]
          (let [{:keys [concept-id]} granule
                response (search/find-refs :granule {:concept-id concept-id})
                es-revision-id (:revision-id (first (:refs response)))]
            (is (= 5 es-revision-id) (str "Failure for granule " concept-id))))))))

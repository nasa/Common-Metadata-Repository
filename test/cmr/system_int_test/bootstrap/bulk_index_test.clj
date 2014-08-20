(ns cmr.system-int-test.bootstrap.bulk_index_test
  "Integration test for CMR bulk indexing."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.config :as config]
            [cmr.dev-system.system :as sys]
            [user :as user]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

;; This test performs the follwing steps:
;; 1. Saves ten collections in metadata db.
;; 2. Saves three granules for each of those collections in metadata db.
;; 3. Ingests ten granules five times each in a separate thread.
;; 4. Concurrently executes a bulk index operation for the provider.
;; 5. Waits for the bulk indexing and granule ingest to complete.
;; 6. Searches for all of the saved/ingested concepts by concept-id.
;; 7. Verifies that the concepts returned by searh have the expected revision ids.
(deftest bulk-index-after-ingest
  ;; only run this test with the external db
  (when (= (type (get-in user/system [:apps :metadata-db :db]))
           cmr.oracle.connection.OracleStore)
    (let [collections (for [x (range 1 11)]
                        (let [cmap {:short-name (str "short-name" x)
                                    :entry-title (str "ttl" x)}
                              umm (dc/collection cmap)
                              xml (echo10/umm->echo10-xml umm)
                              concept-map {:concept-type :collection
                                           :format "application/echo10+xml"
                                           :metadata xml
                                           :extra-fields {:short-name (str "short-name" x)
                                                          :entry-title (str "title" x)
                                                          :version-id "v1"}
                                           :provider-id "PROV1"
                                           :native-id (str "coll" x)
                                           :short-name (str "short-name" x)}]
                          (ingest/save-concept concept-map)))
          granules1 (mapcat (fn [collection]
                                (doall
                                  (for [x (range 1 4)]
                                    (let [pid (:concept-id collection)
                                          cmap {:native-id (str "gran-" pid "-" x)}
                                          umm (dg/granule cmap)
                                          xml (echo10/umm->echo10-xml umm)
                                          concept-map {:concept-type :granule
                                                       :provider-id "PROV1"
                                                       :native-id (str "gran-" pid "-" x)
                                                       :extra-fields {:parent-collection-id pid}
                                                       :format "application/echo10+xml"
                                                       :metadata xml}]
                                      (ingest/save-concept concept-map)))))
                              collections)
          ;; granules2 and f (the future) are used to ingest ten granules five times each in
          ;; a separate thread to verify that bulk indexing with concurrent ingest does the right
          ;; thing.
          granules2 (let [collection (first collections)
                          pid (:concept-id (first collections))]
                      (for [x (range 1 11)]
                        (dg/granule collection {:granule-ur (str "gran2-" pid "-" x)})))
          f (future (doall (for [n (range 1 6)] (doall (map (fn [gran]
                                                              (Thread/sleep 100)
                                                              (d/ingest "PROV1" gran))
                                                            granules2)))))]

      (index/bulk-index-provider "PROV1")
      ;; force our future to complete
      @f
      (index/refresh-elastic-index)

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

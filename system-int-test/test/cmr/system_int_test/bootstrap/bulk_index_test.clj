(ns cmr.system-int-test.bootstrap.bulk-index-test
  "Integration test for CMR bulk indexing."
  (:require
    [clj-time.core :as t]
    [clojure.test :refer :all]
    [cmr.mock-echo.client.echo-util :as e]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.system-int-test.utils.tag-util :as tags]
    [cmr.umm.echo10.echo10-core :as echo10]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       tags/grant-all-tag-fixture]))

(deftest bulk-index-after-date-time
  (s/only-with-real-database
    (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! false))
    (let [;; saved but not indexed
          umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (mdb/save-concept {:concept-type :collection
                                   :format "application/echo10+xml"
                                   :metadata xml1
                                   :extra-fields {:short-name "coll1"
                                                  :entry-title "coll1"
                                                  :entry-id "coll1"
                                                  :version-id "v1"}
                                   :revision-date "2000-01-01T10:00:00Z"
                                   :provider-id "PROV1"
                                   :native-id "coll1"
                                   :short-name "coll1"})
          umm1 (merge umm1 (select-keys coll1 [:concept-id :revision-id]))

          ;; granule
          ummg1 (dg/granule coll1 {:granule-ur "gran1"})
          xmlg1 (echo10/umm->echo10-xml ummg1)
          gran1 (mdb/save-concept {:concept-type :granule
                                   :provider-id "PROV1"
                                   :native-id "gran1"
                                   :format "application/echo10+xml"
                                   :metadata xmlg1
                                   :revision-date "2000-01-01T10:00:00Z"
                                   :extra-fields {:parent-collection-id (:concept-id umm1)
                                                  :parent-entry-title "coll1"
                                                  :granule-ur "ur1"}})
          ummg1 (merge ummg1 (select-keys gran1 [:concept-id :revision-id]))

          user1-token (e/login (s/context) "user1")

          tag1 (mdb/save-concept {:concept-type :tag
                                  :native-id "tag1"
                                  :user-id "user1"
                                  :format "application/edn"
                                  :metadata "{:tag-key \"tag1\" :description \"A good tag\" :originator-id \"user1\"}"
                                  :revision-date "2000-01-01T10:00:00Z"})

          umm2 (dc/collection {:short-name "coll2" :entry-title "coll2"})
          xml2 (echo10/umm->echo10-xml umm2)
          coll2 (mdb/save-concept {:concept-type :collection
                                   :format "application/echo10+xml"
                                   :metadata xml2
                                   :extra-fields {:short-name "coll2"
                                                  :entry-title "coll2"
                                                  :entry-id "coll2"
                                                  :version-id "v1"}
                                   :revision-date "2016-01-01T10:00:00Z"
                                   :provider-id "PROV1"
                                   :native-id "coll2"
                                   :short-name "coll2"})
           umm2 (merge umm2 (select-keys coll2 [:concept-id :revision-id]))


           ; granule
           ummg2 (dg/granule coll2 {:granule-ur "gran2"})
           xmlg2 (echo10/umm->echo10-xml ummg2)
           gran2 (mdb/save-concept {:concept-type :granule
                                    :provider-id "PROV1"
                                    :native-id "gran2"
                                    :format "application/echo10+xml"
                                    :metadata xmlg2
                                    :revision-date "2016-01-01T10:00:00Z"
                                    :extra-fields {:parent-collection-id (:concept-id umm2)
                                                   :parent-entry-title "coll2"
                                                   :granule-ur "ur2"}})
          ummg2 (merge ummg2 (select-keys gran2 [:concept-id :revision-id]))

          tag2 (mdb/save-concept {:concept-type :tag
                                  :native-id "tag2"
                                  :user-id "user1"
                                  :format "application/edn"
                                  :metadata "{:tag-key \"tag2\" :description \"A good tag\" :originator-id \"user1\"}"
                                  :revision-date "2016-01-01T10:00:00Z"})]

      (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! true))

      ;; Verify that all of the ingest requests completed successfully
      (doseq [concept [coll1 coll2 gran1 gran2]] (is (= 201 (:status concept))))

      (bootstrap/bulk-index-after-date-time "2015-01-01T12:00:00Z")
      (index/wait-until-indexed)

      (testing "Only concepts after date are indexed."
               (are3 [search concept-type expected]
                     (d/refs-match? expected (search/find-refs concept-type search))

                     "Collections"
                     {} :collection [umm2]

                     "Granules"
                     {} :granule [ummg2])

               (are3 [query expected-tags]
                     (let [result-tags (tags/search query)
                           {:keys [status hits items took]} result-tags
                           items (map #(select-keys % [:concept-id :revision-id]) items)
                           results {:status status :hits hits :items items :took took}]
                       (tags/assert-tag-search expected-tags results))

                     "Tags"
                     {} [tag2])))))



;; This test runs bulk index with some concepts in mdb that are good, and some that are
;; deleted, and some that have not yet been deleted, but have an expired deletion date.
(deftest bulk-index-with-some-deleted
  (s/only-with-real-database
    (let [;; saved but not indexed
          umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (mdb/save-concept {:concept-type :collection
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
          coll3 (mdb/save-concept {:concept-type :collection
                                   :format "application/echo10+xml"
                                   :metadata xml3
                                   :extra-fields {:short-name "coll3"
                                                  :entry-title "coll3"
                                                  :entry-id "coll3"
                                                  :version-id "v1"
                                                  :delete-time "2000-01-01T12:00:00Z"}
                                   :provider-id "PROV1"
                                   :native-id "coll3"
                                   :short-name "coll3"})
          ;; a granule saved with a nil delete time but an expired delete time in the xml
          ummg1 (dg/granule coll1 {:granule-ur "gran1" :delete-time "2000-01-01T12:00:00Z"})
          xmlg1 (echo10/umm->echo10-xml ummg1)
          gran1 (mdb/save-concept {:concept-type :granule
                                   :provider-id "PROV1"
                                   :native-id "gran1"
                                   :format "application/echo10+xml"
                                   :metadata xmlg1
                                   :extra-fields {:parent-collection-id (:concept-id umm1)
                                                  :parent-entry-title "coll1"
                                                  :granule-ur "ur1"}})]
      (mdb/tombstone-concept coll2-tombstone)

      ;; Verify that all of the ingest requests completed successfully
      (doseq [concept [coll1 coll3 gran1]] (is (= 201 (:status concept))))
      ;; tombstone should return 200
      (is (= 200 (:status coll2)))

      (bootstrap/bulk-index-provider "PROV1")
      (index/wait-until-indexed)

      (testing "Expired documents are not indexed during bulk indexing and deleted documents
               get deleted."
               (are [search concept-type expected]
                    (d/refs-match? expected (search/find-refs concept-type search))
                    {:concept-id (:concept-id coll1)} :collection [umm1]
                    {:concept-id (:concept-id coll2)} :collection []
                    {:concept-id (:concept-id coll3)} :collection []
                    {:concept-id (:concept-id ummg1)} :granule [])))))

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

(deftest invalid-provider-bulk-index-validation-test
  (s/only-with-real-database
    (testing "Validation of a provider supplied in a bulk-index request."
      (let [{:keys [status errors]} (bootstrap/bulk-index-provider "NCD4580")]
        (is (= [400 ["Provider: [NCD4580] does not exist in the system"]]
               [status errors]))))))

(deftest collection-bulk-index-validation-test
  (s/only-with-real-database
    (let [umm1 (dc/collection {:short-name "coll1" :entry-title "coll1"})
          xml1 (echo10/umm->echo10-xml umm1)
          coll1 (mdb/save-concept {:concept-type :collection
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
          gran1 (mdb/save-concept {:concept-type :granule
                                   :provider-id "PROV1"
                                   :native-id "gran1"
                                   :format "application/echo10+xml"
                                   :metadata xmlg1
                                   :extra-fields {:parent-collection-id (:concept-id umm1)
                                                  :parent-entry-title "coll1"}})
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

;; This test is to verify that bulk index works with tombstoned tag associations
(deftest bulk-index-collections-with-tag-association-test
  (s/only-with-real-database
    (let [[coll1 coll2] (for [n (range 1 3)]
                          (d/ingest "PROV1" (dc/collection {:entry-title (str "coll" n)})))
          ;; Wait until collections are indexed so tags can be associated with them
          _ (index/wait-until-indexed)
          user1-token (e/login (s/context) "user1")
          tag1-colls [coll1 coll2]
          tag-key "tag1"
          tag1 (tags/save-tag
                 user1-token
                 (tags/make-tag {:tag-key tag-key})
                 tag1-colls)]

      (index/wait-until-indexed)
      ;; disassociate tag1 from coll2 and not send indexing events
      (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! false))
      (tags/disassociate-by-query user1-token tag-key {:concept_id (:concept-id coll2)})
      (dev-sys-util/eval-in-dev-sys
        `(cmr.metadata-db.config/set-publish-messages! true))

      (bootstrap/bulk-index-provider "PROV1")
      (index/wait-until-indexed)

      (testing "All tag parameters with XML references"
        (is (d/refs-match? [coll1]
                           (search/find-refs :collection {:tag-key "tag1"})))))))

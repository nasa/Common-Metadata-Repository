(ns cmr.system-int-test.bootstrap.bulk-index.delete-test
  "Integration test for CMR bulk index delete operations."
  (:require
   [clj-time.coerce :as cr]
   [clj-time.core :as t]
   [clojure.java.jdbc :as j]
   [clojure.test :refer :all]
   [cmr.access-control.test.util :as u]
   [cmr.common.date-time-parser :as p]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.oracle.connection :as oracle]
   [cmr.system-int-test.bootstrap.bulk-index.core :as core]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as tc]
   [cmr.umm.echo10.echo10-core :as echo10]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                      tags/grant-all-tag-fixture]))

(defn assert-rebalance-status
  [expected-counts collection]
  (is (= (assoc expected-counts :status 200)
         (bootstrap/get-rebalance-status (:concept-id collection)))))

(deftest ^:oracle bulk-delete-by-concept-id
  (s/only-with-real-database
    (let [coll1 (core/save-collection "PROV1" 1)
          coll2 (core/save-collection "PROV1" 2)
          coll2-id (:concept-id coll2)
          coll3 (core/save-collection "PROV1" 3)
          gran1 (core/save-granule "PROV1" 1 coll2)
          gran2 (core/save-granule "PROV1" 2 coll2)
          gran3 (core/save-granule "PROV1" 3 coll2)
          gran4 (core/save-granule "PROV1" 4 coll3)
          gran5 (core/save-granule "PROV1" 5 coll3)
          tag1 (core/save-tag 1)
          tag2 (core/save-tag 2 {})
          acl1 (core/save-acl 1
                              {:extra-fields {:acl-identity "system:token"
                                              :target-provider-id "PROV1"}}
                              "TOKEN")
          acl2 (core/save-acl 2
                              {:extra-fields {:acl-identity "system:group"
                                              :target-provider-id "PROV1"}}
                              "GROUP")
          group1 (core/save-group 1)
          group2 (core/save-group 2 {})
          {:keys [status errors]} (bootstrap/bulk-delete-concepts "PROV1" :collection (map :concept-id [coll1]) nil)]

      (is (= [401 ["You do not have permission to perform that action."]]
             [status errors]))

      ;; Force coll2 granules into their own index to make sure
      ;; granules outside of 1_small_collections get deleted properly.
      (bootstrap/start-rebalance-collection coll2-id)
      (bootstrap/finalize-rebalance-collection coll2-id)
      (index/wait-until-indexed)

      (assert-rebalance-status {:small-collections 0 :separate-index 3 :rebalancing-status "NOT_REBALANCING"} coll2)

      (bootstrap/bulk-delete-concepts "PROV1" :collection (map :concept-id [coll1]))
      (bootstrap/bulk-delete-concepts "PROV1" :granule (map :concept-id [gran1 gran3 gran4]))
      (bootstrap/bulk-delete-concepts "PROV1" :tag [(:concept-id tag1)])
      ;; Commented out until ACLs and groups are supported in the index by concept-id API
      ; (bootstrap/bulk-index-concepts "CMR" :access-group [(:concept-id group2)])
      ; (bootstrap/bulk-index-concepts "CMR" :acl [(:concept-id acl2)])

      (index/wait-until-indexed)

      (testing "Concepts are deleted"
        ;; Collections and granules
        (are3 [concept-type expected]
          (d/refs-match? expected (search/find-refs concept-type {:token (tc/echo-system-token)}))

          "Collections"
          :collection [coll2 coll3]

          "Granules"
          :granule [gran2 gran5])

        (are3 [expected-tags]
            (let [result-tags (update
                                (tags/search {})
                                :items
                                (fn [items]
                                  (map #(select-keys % [:concept-id :revision-id]) items)))]
              (tags/assert-tag-search expected-tags result-tags))

            "Tags"
            [tag2])))))

;; This test runs bulk index with some concepts in mdb that are good, and some that are
;; deleted, and some that have not yet been deleted, but have an expired deletion date.
(deftest ^:oracle bulk-index-with-some-deleted
  (s/only-with-real-database
   ;; Disable message publishing so items are not indexed as part of the initial save.
   (core/disable-automatic-indexing)
   (let [;; coll1 is a regular collection that is ingested
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
         ;; coll2 is a regualr collection that is ingested and will be deleted later
         coll2 (d/ingest "PROV1" (dc/collection {:short-name "coll2" :entry-title "coll2"}))
         ;; coll3 is a collection with an expired delete time
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
         ;; gran1 is a regular granule that is ingested
         ummg1 (dg/granule coll1 {:granule-ur "gran1"})
         xmlg1 (echo10/umm->echo10-xml ummg1)
         gran1 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran1"
                                  :format "application/echo10+xml"
                                  :metadata xmlg1
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :granule-ur "gran1"}})
         ummg1 (merge ummg1 (select-keys gran1 [:concept-id :revision-id]))
         ;; gran2 is a regular granule that is ingested and will be deleted later
         ummg2 (dg/granule coll1 {:granule-ur "gran2"})
         xmlg2 (echo10/umm->echo10-xml ummg2)
         gran2 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran2"
                                  :format "application/echo10+xml"
                                  :metadata xmlg2
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :granule-ur "gran2"}})
         ummg2 (merge ummg2 (select-keys gran2 [:concept-id :revision-id]))
         ;; gran3 is a granule with an expired delete time
         ummg3 (dg/granule coll1 {:granule-ur "gran3" :delete-time "2000-01-01T12:00:00Z"})
         xmlg3 (echo10/umm->echo10-xml ummg3)
         gran3 (mdb/save-concept {:concept-type :granule
                                  :provider-id "PROV1"
                                  :native-id "gran3"
                                  :format "application/echo10+xml"
                                  :metadata xmlg3
                                  :extra-fields {:parent-collection-id (:concept-id umm1)
                                                 :parent-entry-title "coll1"
                                                 :delete-time "2000-01-01T12:00:00Z"
                                                 :granule-ur "gran3"}})]

     ;; Verify that all of the ingest requests completed successfully
     (doseq [concept [coll1 coll2 coll3 gran1 gran2 gran3]] (is (= 201 (:status concept))))
     ;; bulk index all collections and granules
     (bootstrap/bulk-index-provider "PROV1")
     (index/wait-until-indexed)

     (testing "Expired documents are not indexed during bulk indexing"
       (are [search concept-type expected]
         (d/refs-match? expected (search/find-refs concept-type search))
         {:concept-id (:concept-id coll1)} :collection [umm1]
         {:concept-id (:concept-id coll2)} :collection [coll2]
         {:concept-id (:concept-id coll3)} :collection []
         {:concept-id (:concept-id gran1)} :granule [ummg1]
         {:concept-id (:concept-id gran2)} :granule [ummg2]
         {:concept-id (:concept-id gran3)} :granule []))

     (testing "Deleted documents get deleted during bulk indexing"
       (let [coll2-tombstone {:concept-id (:concept-id coll2)
                              :revision-id (inc (:revision-id coll2))}
             gran2-tombstone {:concept-id (:concept-id gran2)
                              :revision-id (inc (:revision-id gran2))}]
         ;; delete coll2 and gran2 in metadata-db
         (mdb/tombstone-concept coll2-tombstone)
         (mdb/tombstone-concept gran2-tombstone)
         ;; bulk index all collections and granules
         (bootstrap/bulk-index-provider "PROV1")
         (index/wait-until-indexed)
         (are [search concept-type expected]
           (d/refs-match? expected (search/find-refs concept-type search))
           {:concept-id (:concept-id coll1)} :collection [umm1]
           {:concept-id (:concept-id coll2)} :collection []
           {:concept-id (:concept-id coll3)} :collection []
           {:concept-id (:concept-id gran1)} :granule [ummg1]
           {:concept-id (:concept-id gran2)} :granule []
           {:concept-id (:concept-id gran3)} :granule []))))

   ;; Re-enable message publishing.
   (core/reenable-automatic-indexing)))

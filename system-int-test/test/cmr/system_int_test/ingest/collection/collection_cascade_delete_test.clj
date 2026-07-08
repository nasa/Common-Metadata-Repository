(ns cmr.system-int-test.ingest.collection.collection-cascade-delete-test
  "Tests cascade delete of collections with individual granule indexes"
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest ^:oracle collection-cascade-delete-removes-index-set-metadata-test
  (s/only-with-real-database
   (testing "Deleting a collection with an individual granule index removes it from the index-set"
     (let [coll (d/ingest "PROV1" (dc/collection {:entry-title "test-collection"}) {:validate-keywords false})
           _ (d/ingest "PROV1" (dg/granule coll {:granule-ur "gran1"}))
           _ (d/ingest "PROV1" (dg/granule coll {:granule-ur "gran2"}))
           concept-id (:concept-id coll)]
       (index/wait-until-indexed)

       (testing "Collection starts in small_collections"
         (let [index-set (index/get-index-set-by-id 1)
               granule-concepts (get-in index-set [:index-set :concepts :granule])]
           (is (contains? granule-concepts :small_collections))
           (is (not (contains? granule-concepts (keyword concept-id))))))

       (testing "Rebalance collection to get an individual granule index"
         (bootstrap/start-rebalance-collection concept-id)
         (index/wait-until-indexed)
         (bootstrap/finalize-rebalance-collection concept-id)
         (index/wait-until-indexed)

         (let [index-set (index/get-index-set-by-id 1)
               granule-concepts (get-in index-set [:index-set :concepts :granule])
               collection-key (keyword concept-id)]
           (is (contains? granule-concepts collection-key)
               "Collection should have an entry in the granule concepts map")
           (is (string? (get granule-concepts collection-key))
               "Collection's granule index name should be a string")))

       (testing "Delete the collection"
         (ingest/delete-concept coll)
         (index/wait-until-indexed))

       (testing "Index-set metadata should no longer contain the collection"
         (let [index-set (index/get-index-set-by-id 1)
               granule-concepts (get-in index-set [:index-set :concepts :granule])
               collection-key (keyword concept-id)]
           (is (not (contains? granule-concepts collection-key))
               "Collection should be removed from the granule concepts map after deletion")))))))

(deftest ^:oracle collection-cascade-delete-in-small-collections-test
  (s/only-with-real-database
   (testing "Deleting a collection in small_collections does not modify index-set"
     (let [coll (d/ingest "PROV1" (dc/collection {:entry-title "small-coll"}) {:validate-keywords false})
           _ (d/ingest "PROV1" (dg/granule coll {:granule-ur "gran1"}))
           concept-id (:concept-id coll)
           index-set-concept-id (mdb/get-concept-id :index-set "CMR" "1")
           get-revision #(get (mdb/get-concept index-set-concept-id) :revision-id)]
       (index/wait-until-indexed)

       (let [initial-revision (get-revision)
             index-set (index/get-index-set-by-id 1)
             granule-concepts (get-in index-set [:index-set :concepts :granule])]
         (is (contains? granule-concepts :small_collections))
         (is (not (contains? granule-concepts (keyword concept-id))))

         (testing "Delete the collection in small_collections"
           (ingest/delete-concept coll)
           (index/wait-until-indexed))

         (testing "Index-set revision should not change"
           (let [final-revision (get-revision)]
             (is (= initial-revision final-revision)
                 "Index-set should not be updated when deleting a collection from small_collections"))))))))

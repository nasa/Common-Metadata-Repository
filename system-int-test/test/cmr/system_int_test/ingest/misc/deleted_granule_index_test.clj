(ns cmr.system-int-test.ingest.misc.deleted-granule-index-test
  "When a rebalanced collection is deleted, the index associated should be removed
   this namespace tests that functionality."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.granule :as granule]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :once (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest cascade-collection-delete-removes-separate-granule-index-test
  (let [collection (data-core/ingest-umm-spec-collection
                    "PROV1"
                    (data-umm-c/collection {})
                    {:validate-keywords false})
        concept-id (:concept-id collection)
        concept-key (keyword concept-id)]
    (testing "When a collection with a separate granule index is deleted, 
              then cascade deletion removes the index and its index-set entries"
      (bootstrap/start-rebalance-collection concept-id)
      (index/wait-until-indexed)
      (bootstrap/finalize-rebalance-collection concept-id)
      (index/wait-until-indexed)

      (let [index-set (index/get-index-set-by-id 1)]
        (is (= 200 (:status index-set)))
        (is (some? (get-in index-set [:index-set :concepts :granule concept-key])))
        (is (some #(= concept-id (:name %))
                  (get-in index-set [:index-set :granule :indexes])))
        (is (= 200 (:status (index/gran-elastic-index-exists? collection)))))

      (ingest/delete-concept (data-core/umm-c-collection->concept collection :echo10) {})
      (index/wait-until-indexed)

      (let [index-set (index/get-index-set-by-id 1)]
        (is (= 200 (:status index-set)))
        (is (nil? (get-in index-set [:index-set :concepts :granule concept-key])))
        (is (not-any? #(= concept-id (:name %))
                      (get-in index-set [:index-set :granule :indexes])))
        (is (= 404 (:status (index/gran-elastic-index-exists? collection))))))))

(deftest deleted-granule-test-index
  (testing "Ingest collection, rebalance collection, delete collection"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1"
                                                           (data-umm-c/collection {})
                                                           {:validate-keywords false})]
      (bootstrap/start-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (bootstrap/finalize-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (let [index-exists-before-delete-response (index/gran-elastic-index-exists? collection)
            _ (ingest/delete-concept (data-core/umm-c-collection->concept collection :echo10) {})
            _ (index/wait-until-indexed)
            index-exists-after-delete-response (index/gran-elastic-index-exists? collection)]
        (is (= 200 (:status index-exists-before-delete-response)))
        (is (= 404 (:status index-exists-after-delete-response))))))
  (testing "Ingest collection, rebalance collection, delete collection, ingest collection, ingest granule, check index exists again"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1"
                                                           (data-umm-c/collection {})
                                                           {:validate-keywords false})]
      (bootstrap/start-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (bootstrap/finalize-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (ingest/delete-concept (data-core/umm-c-collection->concept collection :echo10) {})
      (index/wait-until-indexed)
      (let [index-not-exists-before-reingest-response (index/gran-elastic-index-exists? collection)
            new-collection (data-core/ingest-umm-spec-collection "PROV1"
                                                                 (data-umm-c/collection {})
                                                                 {:validate-keywords false})
            _ (data-core/ingest "PROV1" (update-in (granule/granule-with-umm-spec-collection new-collection (:concept-id new-collection))
                                                   [:collection-ref]
                                                   dissoc :ShortName :Version))
            _ (index/wait-until-indexed)

            index-not-exists-after-reingest-response (index/gran-elastic-index-exists? collection)]
        (is (= 404 (:status index-not-exists-before-reingest-response)))
        (is (= 200 (:status index-not-exists-after-reingest-response)))))))

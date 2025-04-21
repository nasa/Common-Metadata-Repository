(ns cmr.system-int-test.ingest.misc.deleted-collection-index-test
  "When a rebalanced collection is deleted, the index associated should be removed
   this namespace tests that functionality."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :once (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest deleted-collection-test-index
  (testing "Ingest collection, rebalance collection, delete collection"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1"
                                                           (data-umm-c/collection {})
                                                           {:validate-keywords false})]
      (bootstrap/start-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (bootstrap/finalize-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (let [index-exists-before-delete-response (index/check-index-exists collection)
            _ (ingest/delete-concept (data-core/umm-c-collection->concept collection :echo10) {})
            _ (index/wait-until-indexed)
            index-exists-after-delete-response (index/check-index-exists collection)]
        (is (= 200 (:status index-exists-before-delete-response)))
        (is (= 404 (:status index-exists-after-delete-response))))))
  (testing "Ingest collection, rebalance collection, delete collection, ingest collection, ingest granule, check index doesn't exist"
    (let [collection (data-core/ingest-umm-spec-collection "PROV1"
                                                           (data-umm-c/collection {})
                                                           {:validate-keywords false})]
      (bootstrap/start-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (bootstrap/finalize-rebalance-collection (:concept-id collection))
      (index/wait-until-indexed)
      (ingest/delete-concept (data-core/umm-c-collection->concept collection :echo10) {})
      (let [index-not-exists-before-reingest-response (index/check-index-exists collection)
            new-collection (data-core/ingest-umm-spec-collection "PROV1"
                                                                 (data-umm-c/collection {})
                                                                 {:validate-keywords false})
            gran1 (data-core/ingest "PROV1" (update-in (granule/granule-with-umm-spec-collection new-collection (:concept-id new-collection))
                                                       [:collection-ref]
                                                       dissoc :ShortName :Version))
            _ (index/wait-until-indexed)

            index-not-exists-after-reingest-response (index/check-index-exists collection)]
        (is (= 200 (:status index-exists-before-delete-response)))
        (is (= 404 (:status index-exists-after-delete-response)))
        (is (= 404 (:status index-not-exists-before-reingest-response)))
        (is (= 404 (:status index-not-exists-after-reingest-response)))))))

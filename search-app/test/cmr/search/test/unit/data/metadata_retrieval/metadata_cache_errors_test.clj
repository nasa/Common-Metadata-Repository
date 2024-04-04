(ns cmr.search.test.unit.data.metadata-retrieval.metadata-cache-errors-test
  (:require
    [clojure.test :refer :all]
    [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
    [cmr.search.data.metadata-retrieval.metadata-cache :as mc]))

(def create-context
  "Creates a testing concept with the KMS caches."
  {:system {:caches {cmn-coll-metadata-cache/cache-key (cmn-coll-metadata-cache/create-cache)}}})

(deftest get-collection-metadata-cache-concept-ids-test
  (testing "redis connection error"
    (is (thrown? Exception (mc/get-collection-metadata-cache-concept-ids create-context)))))

(deftest get-concept-id
  (testing "redis connection error"
    (is (thrown? Exception (mc/get-concept-id create-context "C1234")))))


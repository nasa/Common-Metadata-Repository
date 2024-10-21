(ns cmr.metadata-db.test.services.subscriptions-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.metadata-db.services.subscriptions :as subscriptions] 
   [cmr.redis-utils.test.test-util :as test-util]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

#_{:clj-kondo/ignore [:unresolved-var]}
(deftest subscription-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache cache-key)
    (testing "Cache is empty"
      (is (nil? (hash-cache/get-map cache cache-key))))
    (testing "Testing if cache is enabled."
      (let [value (mdb-config/ingest-subscription-enabled)]
        (is (= value subscriptions/subscriptions-enabled?))))
    (testing "Testing if a passed in concept is a subscription concept"
      (is (subscriptions/subscription-concept? :subscription {:extra-fields {:endpoint "some-endpoint"}})))
    (testing "Testing if a passed in concept is a granule concept"
      (is (subscriptions/granule-concept? :granule)))
    (testing "Add a subscription"
      (is (= 1 (subscriptions/add-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12345-PROV1"
                                                                                          :mode "All"
                                                                                          :endpoint "some-endpoint"}}))))
    (testing "Delete a subscription"
      (is (= 1 (subscriptions/delete-subscription test-context :subscription {:extra-fields {:collection-concept-id "C12345-PROV1"
                                                                                             :endpoint "some-endpoint"}}))))))
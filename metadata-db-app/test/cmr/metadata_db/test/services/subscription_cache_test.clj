(ns cmr.metadata-db.test.services.subscription-cache-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.metadata-db.services.subscription-cache :as subscription-cache]
   [cmr.common.hash-cache :as hash-cache]
   [cmr.redis-utils.test.test-util :as test-util]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest subscription-cache-test
  (let [cache-key subscription-cache/subscription-cache-key
        test-context {:system {:caches {cache-key (subscription-cache/create-cache-client)}}}
        cache (get-in test-context [:system :caches cache-key])]
    (hash-cache/reset cache cache-key)
    (testing "Cache is empty"
      (is (nil? (hash-cache/get-map cache cache-key))))
    (testing "Add to the cache."
      (is (= 1 (subscription-cache/set-value test-context "C12345-PROV1" {"enabled" true
                                                                          "mode" "All"}))))
    (testing "Get a value"
      (is (= {"enabled" true
              "mode" "All"} 
             (subscription-cache/get-value test-context "C12345-PROV1"))))
    (testing "Remove a value"
      (is (= 1 (subscription-cache/remove-value test-context "C12345-PROV1"))))
    (testing "Validate cache is empty again."
      (is (is (nil? (hash-cache/get-map cache cache-key)))))))

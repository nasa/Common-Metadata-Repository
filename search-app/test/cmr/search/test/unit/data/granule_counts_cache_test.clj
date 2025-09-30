(ns cmr.search.test.unit.data.granule-counts-cache-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.search.data.granule-counts-cache :as granule-counts-cache]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest create-cache-test
  (testing "Creation of granule counts cache"
    (let [gc-cache (granule-counts-cache/create-granule-counts-cache)]
      (is (some? gc-cache) "Cache should not be nil")
      (is (satisfies? cache/CmrCache gc-cache) "Cache should satisfy the CmrCache protocol"))))

(def collection-granule-counts-mock-data
  {"C1-PROV1" 10, "C2-PROV1" 20})

(defn mock-get-collection-granule-counts
  "This is a test mock function to mock getting granule counts from elastic search."
  [_context _provider-ids]
  collection-granule-counts-mock-data)

(deftest next-cache-test
  (testing "Granule count cache operations"
   (let [cache-key granule-counts-cache/granule-counts-cache-key
         test-context {:system {:caches {cache-key (granule-counts-cache/create-granule-counts-cache)}}}
         granule-counts-cache (get-in test-context [:system :caches cache-key])]
     (cache/reset granule-counts-cache)
     (testing "Cache is empty initially"
       (is (nil? (cache/get-value granule-counts-cache cache-key))))
     (testing "Did cache reload using get-granule-counts"
       (is (= collection-granule-counts-mock-data (granule-counts-cache/get-granule-counts test-context nil mock-get-collection-granule-counts))))
     (testing "Add to the cache"
       (let [granule-counts {"C1200000022-PROV1" 2, "C1200000066-PROV1" 1}]
         (is (cache/set-value granule-counts-cache cache-key granule-counts))))
     (testing "Retrieve granule counts from cache"
       (is (= {"C1200000022-PROV1" 2, "C1200000066-PROV1" 1}
              (cache/get-value granule-counts-cache cache-key))))
     (testing "Clear the cache"
       (cache/reset granule-counts-cache)
       (is (nil? (cache/get-value granule-counts-cache cache-key)))))))

(deftest refresh-test
  (testing "Refreshing granule counts cache"
    (let [cache-key granule-counts-cache/granule-counts-cache-key
          test-context {:system {:caches {cache-key (granule-counts-cache/create-granule-counts-cache)}}}]
      (granule-counts-cache/refresh-granule-counts-cache test-context #(mock-get-collection-granule-counts test-context nil))
      (let [cache (get-in test-context [:system :caches cache-key])
            cached-value (cache/get-value cache cache-key)]
        (is (= collection-granule-counts-mock-data cached-value)
            "Cache should be updated with mock granule counts after refresh")
        (granule-counts-cache/clear-granule-counts-cache test-context)
        (is (nil? (cache/get-value cache cache-key))
            "Cache should be cleared after clear operation")))))

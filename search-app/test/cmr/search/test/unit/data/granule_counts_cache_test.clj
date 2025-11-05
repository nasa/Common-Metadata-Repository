(ns cmr.search.test.unit.data.granule-counts-cache-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.search.data.granule-counts-cache :as granule-counts-cache]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(def collection-granule-counts-mock-data
  {"C1-PROV1" 10, "C2-PROV1" 20})

(defn mock-get-collection-granule-counts
  "This is a test mock function to mock getting granule counts from elastic search."
  [& _]
  collection-granule-counts-mock-data)

(defn setup-test-cache
  "Helper function to set up the test context and cache."
  []
  (let [cache-key granule-counts-cache/granule-counts-cache-key
        test-context {:system {:caches {cache-key (granule-counts-cache/create-granule-counts-cache-client)}}}
        granule-counts-cache (get-in test-context [:system :caches cache-key])]
    (cache/reset granule-counts-cache)
    {:test-context test-context
     :cache granule-counts-cache
     :cache-key cache-key}))

(deftest create-cache-test
  (testing "Creation of granule counts cache"
    (let [gc-cache (granule-counts-cache/create-granule-counts-cache-client)]
      (is (some? gc-cache) "Cache should not be nil")
      (is (satisfies? cache/CmrCache gc-cache) "Cache should satisfy the CmrCache protocol"))))

(deftest granule-count-retrieval-and-caching-test
  (let [{:keys [test-context cache cache-key]} (setup-test-cache)]
    (testing "Cache is empty initially"
      (is (nil? (cache/get-value cache cache-key))))

    (testing "Granule count retrieval with provider filtering"
      (granule-counts-cache/refresh-granule-counts-cache test-context mock-get-collection-granule-counts)

      (testing "Retrieve all granule counts"
        (let [result (granule-counts-cache/get-granule-counts test-context nil mock-get-collection-granule-counts)]
          (is (= collection-granule-counts-mock-data result))))

      (testing "Retrieve granule counts for specific provider"
        (let [result (granule-counts-cache/get-granule-counts test-context ["PROV1"] mock-get-collection-granule-counts)]
          (is (= {"C1-PROV1" 10, "C2-PROV1" 20} result))))

      (testing "Retrieve granule counts for non-existent provider"
        (let [result (granule-counts-cache/get-granule-counts test-context ["PROV3"] mock-get-collection-granule-counts)]
          (is (= {} result)))))

    (testing "Cache operations"
      (testing "Add to the cache"
        (let [granule-counts {"C1200000022-PROV1" 2, "C1200000066-PROV1" 1}]
          (is (cache/set-value cache cache-key granule-counts))))

      (testing "Retrieve granule counts from cache"
        (is (= {"C1200000022-PROV1" 2, "C1200000066-PROV1" 1}
               (cache/get-value cache cache-key))))

      (testing "Clear the cache"
        (cache/reset cache)
        (is (nil? (cache/get-value cache cache-key)))))))

(deftest refresh-test
  (let [{:keys [test-context cache cache-key]} (setup-test-cache)]
    (testing "Refreshing granule counts cache"
      (granule-counts-cache/refresh-granule-counts-cache test-context mock-get-collection-granule-counts)
      (let [cached-value (cache/get-value cache cache-key)]
        (is (= collection-granule-counts-mock-data cached-value)
            "Cache should be updated with mock granule counts after refresh")))))

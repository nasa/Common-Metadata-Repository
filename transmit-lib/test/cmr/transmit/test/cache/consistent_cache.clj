(ns cmr.transmit.test.cache.consistent-cache
  "Unit tests for the consistent cache. It tests everything using in memory caches."
  (:require
   [clojure.test :refer :all]
   [cmr.transmit.cache.consistent-cache :as cc]
   [cmr.transmit.cache.consistent-cache-spec :as consistent-cache-spec]
   [cmr.common.cache :as cache]
   [cmr.common.cache.cache-spec :as cache-spec]
   [cmr.common.cache.fallback-cache :as fallback-cache]
   [cmr.common.cache.fallback-cache-spec :as fallback-cache-spec]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.cache.single-thread-lookup-cache :as slc]
   [cmr.common.lifecycle :as l]))

;; Test the consistent cache with just in memory caches
(deftest consistent-cache-functions-as-cache-test
  (cache-spec/assert-cache (cc/create-consistent-cache
                            (mem-cache/create-in-memory-cache)
                            (mem-cache/create-in-memory-cache))))

(deftest consistent-cache-functions-as-consistent-cache-test
  (let [timeout 10
        hash-cache (mem-cache/create-in-memory-cache)]
    (consistent-cache-spec/assert-consistent-cache
     (cc/create-consistent-cache (mem-cache/create-in-memory-cache)
                                 (cc/fallback-with-timeout hash-cache timeout))
     (cc/create-consistent-cache (mem-cache/create-in-memory-cache)
                                 (cc/fallback-with-timeout hash-cache timeout))
     timeout)))

(deftest consistent-cache-with-single-thread-cache-functions-as-consistent-cache-test
  (let [timeout 10
        hash-cache (mem-cache/create-in-memory-cache)
        slc-cache1 (l/start (slc/create-single-thread-lookup-cache
                             (cc/create-consistent-cache
                              (mem-cache/create-in-memory-cache)
                              (cc/fallback-with-timeout hash-cache timeout)))
                            nil)
        slc-cache2 (l/start (slc/create-single-thread-lookup-cache
                             (cc/create-consistent-cache
                              (mem-cache/create-in-memory-cache)
                              (cc/fallback-with-timeout hash-cache timeout)))
                            nil)]
    (try
      (consistent-cache-spec/assert-consistent-cache slc-cache1 slc-cache2 timeout)
      (finally
        (l/stop slc-cache1 nil)
        (l/stop slc-cache2 nil)))))

(deftest consistent-cache-with-fallback-cache-test
  (let [fake-redis-cache (mem-cache/create-in-memory-cache)
        primary-cache (cc/create-consistent-cache
                       (mem-cache/create-in-memory-cache)
                       fake-redis-cache)
        backup-cache fake-redis-cache ;; And the backup cache
        fallback-cache (fallback-cache/create-fallback-cache primary-cache backup-cache)]
    (cache-spec/assert-cache fallback-cache)
    (fallback-cache-spec/assert-fallback-cache fallback-cache primary-cache backup-cache)))

(deftest cache-size-test
  (testing "Given an empty cache"
    (let [consistent-cache (cc/create-consistent-cache
                            (mem-cache/create-in-memory-cache)
                            (mem-cache/create-in-memory-cache))]
      (testing "when checking the cache size"
        (is (zero? (cache/cache-size consistent-cache))
            "Then the cache size is zero"))
      
      (testing "when putting something into the cache"
        (cache/set-value consistent-cache "test-content-key" "test-content")
        (is (= 44 (cache/cache-size consistent-cache))
            "Then the cache size is the combined hash and content sizes")))))

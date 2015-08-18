(ns cmr.common-app.test.cache.consistent-cache
  "Unit tests for the consistent cache. It tests everything using in memory caches."
  (:require [clojure.test :refer :all]
            [cmr.common-app.cache.consistent-cache :as cc]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common-app.cache.consistent-cache-spec :as consistent-cache-spec]
            [cmr.common.cache :as c]
            [cmr.common.cache.single-thread-lookup-cache :as slc]
            [cmr.common.lifecycle :as l]))

;; Test the consistent cache with just in memory caches
(deftest consistent-cache-functions-as-cache-test
  (cache-spec/assert-cache (cc/create-consistent-cache
                             (mem-cache/create-in-memory-cache)
                             (mem-cache/create-in-memory-cache))))

(deftest consistent-cache-functions-as-consistent-cache-test
  (let [hash-cache (mem-cache/create-in-memory-cache)]
    (consistent-cache-spec/assert-consistent-cache
      (cc/create-consistent-cache (mem-cache/create-in-memory-cache) hash-cache)
      (cc/create-consistent-cache (mem-cache/create-in-memory-cache) hash-cache))))

(deftest consistent-cache-with-single-thread-cache-functions-as-consistent-cache-test
  (let [hash-cache (mem-cache/create-in-memory-cache)
        slc-cache1 (l/start (slc/create-single-thread-lookup-cache
                               (cc/create-consistent-cache
                                 (mem-cache/create-in-memory-cache)
                                 hash-cache))
                            nil)
        slc-cache2 (l/start (slc/create-single-thread-lookup-cache
                               (cc/create-consistent-cache
                                 (mem-cache/create-in-memory-cache)
                                 hash-cache))
                            nil)]
    (try
      (consistent-cache-spec/assert-consistent-cache slc-cache1 slc-cache2)
      (finally
        (l/stop slc-cache1 nil)
        (l/stop slc-cache2 nil)))))


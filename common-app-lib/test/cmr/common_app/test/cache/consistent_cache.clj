(ns cmr.common-app.test.cache.consistent-cache
  "Unit tests for the consistent cache. It tests everything using in memory caches."
  (:require [clojure.test :refer :all]
            [cmr.common-app.cache.consistent-cache :as cc]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common-app.cache.consistent-cache-spec :as consistent-cache-spec]
            [cmr.common.cache :as c]))

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

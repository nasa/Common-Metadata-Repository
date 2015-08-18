(ns cmr.common.test.cache.fallback-cache
  "Unit tests for the fallback cache."
  (:require [clojure.test :refer :all]
            [cmr.common.cache.fallback-cache :as fallback-cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.cache.fallback-cache-spec :as fallback-cache-spec]))

(deftest fallback-cache-functions-as-cache-test
  (cache-spec/assert-cache (fallback-cache/create-fallback-cache
                             (mem-cache/create-in-memory-cache)
                             (mem-cache/create-in-memory-cache))))

(deftest fallback-cache-functions-as-fallback-cache-test
  (let [primary-cache (mem-cache/create-in-memory-cache)
        backup-cache (mem-cache/create-in-memory-cache)
        fallback-cache (fallback-cache/create-fallback-cache primary-cache backup-cache)]
    (fallback-cache-spec/assert-fallback-cache fallback-cache primary-cache backup-cache)))


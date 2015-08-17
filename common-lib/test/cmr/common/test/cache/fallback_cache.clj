(ns cmr.common.test.cache.fallback-cache
  "Unit tests for the fallback cache."
  (:require [clojure.test :refer :all]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [cmr.common.cache.fallback-cache :as fallback-cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.cache.fallback-cache-spec :as fallback-cache-spec]
            [cmr.common.cache :as c]))

(deftest fallback-cache-functions-as-cache-test
  (cache-spec/assert-cache (fallback-cache/create-fallback-cache
                             (mem-cache/create-in-memory-cache)
                             (mem-cache/create-in-memory-cache))))

(deftest fallback-cache-functions-as-fallback-cache-test
  (let [primary-cache (mem-cache/create-in-memory-cache)
        backup-cache (mem-cache/create-in-memory-cache)
        fallback-cache (fallback-cache/create-fallback-cache primary-cache backup-cache)]
    (fallback-cache-spec/assert-fallback-cache fallback-cache primary-cache backup-cache)))

;; TODO Figure out why a cubby cache for the hash cache does not work
(deftest fallback-cache-using-consistent-cache-and-cubby-functions-as-fallback-cache-test
  (let [primary-cache (consistent-cache/create-consistent-cache
                        (mem-cache/create-in-memory-cache) (mem-cache/create-in-memory-cache))
        backup-cache (cubby-cache/create-cubby-cache)
        fallback-cache (fallback-cache/create-fallback-cache primary-cache backup-cache)]
    (fallback-cache-spec/assert-fallback-cache fallback-cache primary-cache backup-cache)))

(ns cmr.system-int-test.common-app-cache-test
  "This tests the cache implementations in the common-app library. These caches require use of a
  running cubby application."
  (:require [clojure.test :refer :all]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [cmr.common-app.cache.consistent-cache-spec :as consistent-cache-spec]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [cmr.common.cache :as c]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.cubby.test.utils :as cubby-test-util]))

(use-fixtures :each cubby-test-util/reset-fixture)

(deftest cubby-cache-functions-as-cache-test
  (cache-spec/assert-cache (cubby-cache/create-cubby-cache)))

;; Test the consistent cache backed by the real cubby cache
(deftest consistent-cache-functions-as-cache-test
  (cache-spec/assert-cache (consistent-cache/create-consistent-cache)))

(deftest consistent-cache-functions-as-consistent-cache-test
  (consistent-cache-spec/assert-consistent-cache
    (consistent-cache/create-consistent-cache)
    (consistent-cache/create-consistent-cache)))


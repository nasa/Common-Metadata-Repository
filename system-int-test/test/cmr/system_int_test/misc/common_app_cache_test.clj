(ns cmr.system-int-test.misc.common-app-cache-test
  "This tests the cache implementations in the common-app library. These caches require use of a
  running redis application."
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.common.cache.cache-spec :as cache-spec]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.transmit.cache.consistent-cache-spec :as consistent-cache-spec]))

(def redis-options
  "The options to pass into redis when creating the cache. For the cache spec we test that calls
  to reset remove the :foo and :bar keys and their hash key counterparts."
  {:keys-to-track [:foo :bar ":foo-hash-code" ":bar-hash-code"]})

(deftest redis-cache-functions-as-cache-test
  (cache-spec/assert-cache (redis-cache/create-redis-cache redis-options)
                           false))

;; Test the consistent cache backed by the real redis cache
(deftest consistent-cache-functions-as-cache-test
  (cache-spec/assert-cache (consistent-cache/create-consistent-cache redis-options)
                           false))

(deftest consistent-cache-functions-as-consistent-cache-test
  (consistent-cache-spec/assert-consistent-cache
    (consistent-cache/create-consistent-cache redis-options)
    (consistent-cache/create-consistent-cache redis-options)
    (consistent-cache/consistent-cache-default-hash-timeout-seconds)))

(deftest redis-reset-only-deletes-specified-keys
  (let [redis-cache (redis-cache/create-redis-cache redis-options)]
    (cache/set-value redis-cache :alpha "Alpha value")
    (cache/set-value redis-cache :beta "Beta value")
    (cache/set-value redis-cache :foo "Foo value")
    (testing "Initial cached values are set correctly"
      (is (= "Alpha value" (cache/get-value redis-cache :alpha)))
      (is (= "Beta value" (cache/get-value redis-cache :beta)))
      (is (= "Foo value" (cache/get-value redis-cache :foo))))
    (testing "All cached keys are listed"
      (let [cached-keys (set (cache/get-keys redis-cache))]
        (are [the-key]
          (contains? cached-keys the-key)
          :alpha
          :beta
          :foo)))
    (testing "Reset cache"
      (cache/reset redis-cache)
      (let [cached-keys (set (cache/get-keys redis-cache))]
        (testing "Foo key is deleted from the cache since it is tracked"
          (is (not (contains? cached-keys :foo)))
          (is (nil? (cache/get-value redis-cache :foo))))
        (testing "Alpha and beta keys remain in the cache since they are not tracked"
          (is (contains? cached-keys :alpha))
          (is (contains? cached-keys :beta))
          (is (= "Alpha value" (cache/get-value redis-cache :alpha)))
          (is (= "Beta value" (cache/get-value redis-cache :beta))))))))

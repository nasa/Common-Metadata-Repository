(ns cmr.system-int-test.common-app-cache-test
  "This tests the cache implementations in the common-app library. These caches require use of a
  running cubby application."
  (:require [clojure.test :refer :all]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]
            [cmr.common-app.cache.consistent-cache-spec :as consistent-cache-spec]
            [cmr.common-app.cache.cubby-cache :as cubby-cache]
            [cmr.common.cache :as cache]
            [cmr.common.cache.cache-spec :as cache-spec]))

(def cubby-options
  "The options to pass into cubby when creating the cache. For the cache spec we test that calls
  to reset remove the :foo and :bar keys and their hash key counterparts."
  {:keys-to-track [:foo :bar ":foo-hash-code" ":bar-hash-code"]})

(deftest cubby-cache-functions-as-cache-test
  (cache-spec/assert-cache (cubby-cache/create-cubby-cache cubby-options)
                           false))

;; Test the consistent cache backed by the real cubby cache
(deftest consistent-cache-functions-as-cache-test
  (cache-spec/assert-cache (consistent-cache/create-consistent-cache cubby-options)
                           false))

(deftest consistent-cache-functions-as-consistent-cache-test
  (consistent-cache-spec/assert-consistent-cache
    (consistent-cache/create-consistent-cache cubby-options)
    (consistent-cache/create-consistent-cache cubby-options)
    (consistent-cache/consistent-cache-default-hash-timeout-seconds)))

(deftest cubby-reset-only-deletes-specified-keys
  (let [cubby-cache (cubby-cache/create-cubby-cache cubby-options)]
    (cache/set-value cubby-cache :alpha "Alpha value")
    (cache/set-value cubby-cache :beta "Beta value")
    (cache/set-value cubby-cache :foo "Foo value")
    (testing "Initial cached values are set correctly"
      (is (= "Alpha value" (cache/get-value cubby-cache :alpha)))
      (is (= "Beta value" (cache/get-value cubby-cache :beta)))
      (is (= "Foo value" (cache/get-value cubby-cache :foo))))
    (testing "All cached keys are listed"
      (let [cached-keys (set (cache/get-keys cubby-cache))]
        (are [the-key]
          (contains? cached-keys the-key)
          :alpha
          :beta
          :foo)))
    (testing "Reset cache"
      (cache/reset cubby-cache)
      (let [cached-keys (set (cache/get-keys cubby-cache))]
        (testing "Foo key is deleted from the cache since it is tracked"
          (is (not (contains? cached-keys :foo)))
          (is (nil? (cache/get-value cubby-cache :foo))))
        (testing "Alpha and beta keys remain in the cache since they are not tracked"
          (is (contains? cached-keys :alpha))
          (is (contains? cached-keys :beta))
          (is (= "Alpha value" (cache/get-value cubby-cache :alpha)))
          (is (= "Beta value" (cache/get-value cubby-cache :beta))))))))

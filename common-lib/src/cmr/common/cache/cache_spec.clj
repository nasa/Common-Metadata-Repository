(ns cmr.common.cache.cache-spec
  "Defines a common set of tests for checking cache implementations"
  (:require [clojure.test :refer [are is testing]]
            [cmr.common.cache :as cache]
            [cmr.common.cache.spec-util :as su]))

(defn- check-initial-cache-state
  [cache]
  (is (empty? (cache/get-keys cache)))
  (is (nil? (cache/get-value cache :foo))))

(defn always-fail
  "A function that will always throw an exception. Use this when you want to check the lookup
  function isn't called"
  []
  (throw (Exception. "Always failing")))

(defn- value-type-support
  "Checks that different kinds of values can be stored and retrieved in the cache."
  [cache]
  (are [v]
       (do
         (cache/set-value cache :foo v)
         (= v
            (cache/get-value cache :foo)
            (cache/get-value cache :foo always-fail)))
       :a
       "b"
       false
       true
       'c
       \d
       1
       2.0
       [1 2 3]
       #{1 2 3}
       {:a 1 :b "2"}))

(defn- key-type-support
  "Checks that different kinds of clojure values can be used as keys"
  [cache]
  (cache/set-value cache :foo "keyword")
  (cache/set-value cache ":foo" "keyword string")
  (cache/set-value cache "foo" "string")
  (cache/set-value cache 'foo "symbol")

  (is (= "keyword" (cache/get-value cache :foo)))
  (is (= "keyword string" (cache/get-value cache ":foo")))
  (is (= "string" (cache/get-value cache "foo")))
  (is (= "symbol" (cache/get-value cache 'foo)))

  (su/assert-cache-keys [:foo ":foo" "foo" 'foo] cache true)

  (are [k]
       (do
         (cache/set-value cache k 1)
         (= 1
            (cache/get-value cache k)
            (cache/get-value cache k always-fail)))
       :a
       "b"
       'c
       \d
       1
       2.0
       [1 2 3]
       {:a 1 :b [2 2.0] :c #{1 2 3 4 5}}))

(defn- clear-cache-test
  "Checks that a cache removes values after cleared"
  [cache]
  (cache/set-value cache :foo 1)
  (cache/set-value cache :bar 2)
  (cache/reset cache)
  (is (nil? (cache/get-value cache :foo))))

(defn- get-value-with-lookup-fn-test
  "Checks that the lookup function is used correctly when retrieving cache values"
  [cache]
  (let [counter (atom 0)
        lookup-fn #(swap! counter inc)]

    ;; lookup function is used the first time
    (is (= 1 (cache/get-value cache :foo lookup-fn)))
    ;; The value is cached now
    (is (= 1 (cache/get-value cache :foo always-fail)))
    (is (= 1 @counter))

    (cache/reset cache)
    ;; lookup function used after cache has been cleared
    (is (= 2 (cache/get-value cache :foo lookup-fn)))
    ;; The value is cached now
    (is (= 2 (cache/get-value cache :foo always-fail)))
    (is (= 2 @counter))))

(def ^:private cache-test-fns
  "Defines the minimum set of test functions that check a cache implementation"
  [#'value-type-support
   #'key-type-support
   #'get-value-with-lookup-fn-test
   #'clear-cache-test])

(defn- get-cache-test-fns
  "Returns the functions to test for verifying the cache. Not all caches will delete all values
  from the cache on calls to reset."
  [test-initial-state]
  (if test-initial-state
    (concat [#'check-initial-cache-state] cache-test-fns [#'check-initial-cache-state])
    cache-test-fns))

(defn assert-cache
  "Checks a cache implementation to make sure it behaves as expected."
  ([cache]
   (assert-cache cache true))
  ([cache test-initial-state]
   (doseq [test-fn-var (get-cache-test-fns test-initial-state)]
     (cache/reset cache)
     (testing (:name (meta test-fn-var))
       ((var-get test-fn-var) cache)))))

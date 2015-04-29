(ns cmr.common.cache.cache-spec
  "Defines a common set of tests for checking cache implementations"
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]))

(defn- check-initial-cache-state
  [cache]
  (is (empty? (c/get-keys cache)))
  (is (nil? (c/get-value cache :foo))))

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
         (c/set-value cache :foo v)
         (= v
            (c/get-value cache :foo)
            (c/get-value cache :foo always-fail)))
       :a
       "b"
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
  (c/set-value cache :foo "keyword")
  (c/set-value cache ":foo" "keyword string")
  (c/set-value cache "foo" "string")
  (c/set-value cache 'foo "symbol")

  (is (= "keyword" (c/get-value cache :foo)))
  (is (= "keyword string" (c/get-value cache ":foo")))
  (is (= "string" (c/get-value cache "foo")))
  (is (= "symbol" (c/get-value cache 'foo)))

  (is (= (set [:foo ":foo" "foo" 'foo])
         (set (c/get-keys cache))))

  (are [k]
       (do
         (c/set-value cache k 1)
         (= 1
            (c/get-value cache k)
            (c/get-value cache k always-fail)))
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
  (c/set-value cache :foo 1)
  (c/set-value cache :bar 2)
  (c/reset cache)
  (check-initial-cache-state cache))

(defn- get-value-with-lookup-fn-test
  "Checks that the lookup function is used correctly when retrieving cache values"
  [cache]
  (let [counter (atom 0)
        lookup-fn #(swap! counter inc)]

    ;; lookup function is used the first time
    (is (= 1 (c/get-value cache :foo lookup-fn)))
    ;; The value is cached now
    (is (= 1 (c/get-value cache :foo always-fail)))
    (is (= 1 @counter))

    (c/reset cache)
    ;; lookup function used after cache has been cleared
    (is (= 2 (c/get-value cache :foo lookup-fn)))
    ;; The value is cached now
    (is (= 2 (c/get-value cache :foo always-fail)))
    (is (= 2 @counter))))

(def ^:private cache-test-fns
  "Defines the set of test functions that check a cache implementation"
  [#'check-initial-cache-state
   #'value-type-support
   #'key-type-support
   #'clear-cache-test
   #'get-value-with-lookup-fn-test])

(defn assert-cache
  "Checks a cache implementation to make sure it behaves as expected."
  [cache]
  (doseq [test-fn-var cache-test-fns]
    (c/reset cache)
    (testing (:name (meta test-fn-var))
      ((var-get test-fn-var) cache))))
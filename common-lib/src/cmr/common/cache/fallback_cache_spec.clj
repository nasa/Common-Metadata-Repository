(ns cmr.common.cache.fallback-cache-spec
  "Defines a common set of tests for a fallback cache."
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as c]
   [cmr.common.cache.spec-util :as su]))

(defn- basic-fallback-test
  "Tests the fallback cache works as expected with regards to the primary and backup store."
  [fallback-cache primary-cache backup-cache]
  (let [caches [primary-cache backup-cache]
        initial-values {:foo "foo value" :bar "bar value"}]
    ;; Put the same items in both caches
    (su/put-values-in-caches caches initial-values)

    (testing "Initial state with items in both caches"
      (su/assert-values-in-caches (conj caches fallback-cache) initial-values)
      (su/assert-cache-keys [:foo :bar] primary-cache true)
      (su/assert-cache-keys [:foo :bar] backup-cache true)
      (su/assert-cache-keys [:foo :bar] fallback-cache true))

    (testing "Change a value in the backup cache"
      (c/set-value backup-cache :foo "new foo value")

      (testing "The value is unchanged in the primary cache"
        (is (= "foo value" (c/get-value primary-cache :foo))))

      (testing "Value is retrieved from the primary cache when present in both"
        (is (= "foo value" (c/get-value fallback-cache :foo)))))

    (testing "Add a value to the backup cache"
      (c/set-value backup-cache :alpha "alpha value")

      (testing "Cache keys contain all keys found in either primary or backup cache"
        (su/assert-cache-keys [:foo :bar :alpha] fallback-cache true))

      (testing "The key and value are not present in the primary cache"
        (su/assert-cache-keys [:foo :bar] primary-cache true)
        (is (nil? (c/get-value primary-cache :alpha))))

      (testing "Fallback cache uses the backup cache if value is not found in primary"
        (is (= "alpha value" (c/get-value fallback-cache :alpha))))

      (testing "The value is now present in the primary cache"
        (is (= "alpha value" (c/get-value primary-cache :alpha)))))

    (testing "Nil returned when value is not in either cache"
      (is (nil? (c/get-value fallback-cache :beta)))

      (testing "Key with nil value is not added to the cache"
        (su/assert-cache-keys [:foo :bar :alpha] fallback-cache true)))

    (testing "Add a value to the fallback cache"
      (c/set-value fallback-cache :omega "The last test")

      (testing "The value is added to the primary cache"
        (is (= "The last test" (c/get-value primary-cache :omega))))

      (testing "The value is added to the backup cache"
        (is (= "The last test" (c/get-value backup-cache :omega)))))))

(defn- clear-cache-test
  "Tests that clear cache on the fallback cache clears both primary and backup stores."
  [fallback-cache primary-cache backup-cache]
  (let [initial-values {:foo "foo value" :bar "bar value"}]
    (su/put-values-in-caches [fallback-cache] initial-values)
    (su/assert-cache-keys [:foo :bar] primary-cache true)
    (su/assert-cache-keys [:foo :bar] backup-cache true)
    (c/reset fallback-cache)
    (is (empty? (c/get-keys fallback-cache)))
    (is (empty? (c/get-keys primary-cache)))
    (is (empty? (c/get-keys backup-cache)))
    (su/assert-values-in-caches [fallback-cache primary-cache backup-cache] {:foo nil :bar nil})))

(defn- get-value-with-lookup-test
  "Tests that lookup function can be used when retrieving values on a fallback cache."
  [fallback-cache primary-cache backup-cache]
  (c/set-value primary-cache :foo "foo value")
  (c/set-value backup-cache :bar "bar value")
  (let [lookup-fn (constantly "lookup function value")]
    (testing "Lookup function is ignored if value is in primary cache"
      (is (= "foo value" (c/get-value fallback-cache :foo lookup-fn))))

    (testing "Lookup function is ignored if value is in backup cache"
      (is (= "bar value" (c/get-value fallback-cache :bar lookup-fn))))

    (testing "Lookup function is used if value is not in either cache"
      (is (= "lookup function value" (c/get-value fallback-cache :alpha lookup-fn))))

    (testing "Value added by lookup function is in primary cache"
     (is (= "lookup function value" (c/get-value primary-cache :alpha))))

    (testing "Value added by lookup function is in backup cache"
      (is (= "lookup function value" (c/get-value backup-cache :alpha)))))

  (testing "Get keys returns all of the keys"
   (su/assert-cache-keys [:foo :bar :alpha] fallback-cache true)))

(def ^:private cache-test-fns
  "Defines the set of test functions that check a cache implementation"
  [#'basic-fallback-test
   #'clear-cache-test
   #'get-value-with-lookup-test])

(defn assert-fallback-cache
  "Checks a fallback cache implementation to make sure it behaves as expected. The primary-cache
  and backup-cache passed in must be the primary and backup caches used within the passed in
  fallback-cache."
  [fallback-cache primary-cache backup-cache]
  (doseq [test-fn-var cache-test-fns]
    (c/reset fallback-cache)
    (testing (:name (meta test-fn-var))
      ((var-get test-fn-var) fallback-cache primary-cache backup-cache))))

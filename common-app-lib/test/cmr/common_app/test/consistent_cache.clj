(ns cmr.common-app.test.consistent-cache
  (:require [clojure.test :refer :all]
            [cmr.common-app.consistent-cache :as cc]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache :as c]))

(defn create-consistent-cache
  "Creates one consistent cache with in memory caches"
  []
  (cc/create-consistent-cache
    (mem-cache/create-in-memory-cache)
    (mem-cache/create-in-memory-cache)))

(defn create-consistent-caches
  "Creates two consistent caches that share the same hash code cache."
  []
  (let [hash-cache (mem-cache/create-in-memory-cache)]
    [(cc/create-consistent-cache (mem-cache/create-in-memory-cache) hash-cache)
     (cc/create-consistent-cache (mem-cache/create-in-memory-cache) hash-cache)]))

(defn put-values-in-caches
  "Puts the key value pairs in the val map into the caches"
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (c/set-value cache k v)))

(defn assert-values-in-caches
  "Asserts that all of the values in the value map are in the caches."
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (is (= v (c/get-value cache k)))))

(defn assert-cache-keys
  [expected-keys cache]
  (is (= (sort expected-keys) (sort (c/get-keys cache)))))


(deftest consistentency-test
  (testing "Initial cache state"
    (let [cache (create-consistent-cache)]
      (is (empty? (c/get-keys cache)))
      (is (nil? (c/get-value cache :foo)))))

  (testing "Basic consistency test"
    (let [caches (create-consistent-caches)
          [cache-a cache-b] caches
          initial-values {:foo "foo value" :bar "bar value"}]
      ;; Put the same items in both caches
      (put-values-in-caches caches initial-values)

      (testing "Initial state with items in both caches"
        (assert-values-in-caches caches initial-values)
        (assert-cache-keys [:foo :bar] cache-a)
        (assert-cache-keys [:foo :bar] cache-b))

      (testing "Change a value in one cache"
        (c/set-value cache-a :foo "new foo value")

        (testing "The value is retrievable in the same cache"
          (is (= "new foo value" (c/get-value cache-a :foo))))

        (testing "Other cache should no longer have the old value"
          (is (nil? (c/get-value cache-b :foo))))

        (testing "The other cache keys should not be effected"
          (assert-values-in-caches caches {:bar "bar value"}))

        (testing "Keys after change should be correct"
          (assert-cache-keys [:foo :bar] cache-a)
          (assert-cache-keys [:bar] cache-b)))

      (testing "Change a value in the other cache"
        (c/set-value cache-b :foo "another foo value")

        (testing "The value is retrievable in the same cache"
          (is (= "another foo value" (c/get-value cache-b :foo))))

        (testing "Other cache should no longer have the old value"
          (is (nil? (c/get-value cache-a :foo))))

        (testing "The other cache keys should not be effected"
          (assert-values-in-caches caches {:bar "bar value"}))

        (testing "Keys after change should be correct"
          (assert-cache-keys [:bar] cache-a)
          (assert-cache-keys [:foo :bar] cache-b)))))

  (testing "Clear cache test"
    (let [caches (create-consistent-caches)
          [cache-a cache-b] caches
          initial-values {:foo "foo value" :bar "bar value"}]
      (put-values-in-caches caches initial-values)

      (c/reset cache-a)
      (is (empty? (c/get-keys cache-a)))
      (is (empty? (c/get-keys cache-b)))
      (assert-values-in-caches caches {:foo nil :bar nil})
      cache-b)))

;; This tests that get value with a lookup function will perform correctly. The names of objects in
;; the test were chosen based on the use case for adding the consistent cache. They represent an in
;; memory version of the real thing
(deftest get-value-with-lookup-test
  (let [;; Represents the caches within the indexer application. They would be kept consistent with
        ;; a hash cache using cubby in the real version.
        [indexer1-cache indexer2-cache] (create-consistent-caches)
        ;; Represents ECHO's storage of ACLs. Acls here are just a list of symbols
        echo-acls-atom (atom [:acl1 :acl2])
        ;; The lookup function for "fetching" the latest version of the acls
        lookup-fn #(deref echo-acls-atom)

        ;; Checks that acls retrieved from the cache are the expected values.
        ;; Since we provide a lookup function and they're using a consistent cache the values
        ;; should always be correct.
        assert-acls-from-cache (fn [expected-acls]
                                 (is (= expected-acls (c/get-value indexer1-cache :acls lookup-fn)))
                                 (is (= expected-acls (c/get-value indexer2-cache :acls lookup-fn))))]


    (testing "First lookup with empty caches"
      (assert-acls-from-cache [:acl1 :acl2]))

    (testing "Acls have changed"
      (swap! echo-acls-atom conj :acl3)

      (testing "Old values are still cached"
        (assert-acls-from-cache [:acl1 :acl2]))

      (testing "One cache was cleared"
        (c/reset indexer1-cache)
        (assert-acls-from-cache [:acl1 :acl2 :acl3]))

      (testing "Manually updated one cache"
        (swap! echo-acls-atom conj :acl4)
        (c/set-value indexer2-cache :acls (lookup-fn))
        (assert-acls-from-cache [:acl1 :acl2 :acl3 :acl4])))))
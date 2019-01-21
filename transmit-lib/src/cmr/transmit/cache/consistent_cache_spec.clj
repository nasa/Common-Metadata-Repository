(ns cmr.transmit.cache.consistent-cache-spec
  "Defines a common set of tests for a consistent cache."
  (:require
   [clojure.test :refer [is testing]]
   [cmr.common.cache :as c]
   [cmr.common.cache.spec-util :as su]
   [cmr.common.time-keeper :as time-keeper]))

(defn basic-consistency-test
  "Takes two different consistent caches which should be configured with a shared hash cache.
   It will test that the cache behaves as expected returning consistent values when one changes."
  [cache-a cache-b hash-timeout]
  (time-keeper/with-frozen-time
   (let [caches [cache-a cache-b]
         initial-values {:foo "foo value" :bar "bar value"}]
     ;; Put the same items in both caches
     (su/put-values-in-caches caches initial-values)

     (testing "Initial state with items in both caches"
       (su/assert-values-in-caches caches initial-values)
       (su/assert-cache-keys [:foo :bar] cache-a)
       (su/assert-cache-keys [:foo :bar] cache-b))

     (testing "Change a value in one cache"
       (c/set-value cache-a :foo "new foo value")

       (testing "The value is retrievable in the same cache"
         (is (= "new foo value" (c/get-value cache-a :foo))))

       (testing "Other cache still has the old value until time passes"
         (is (= "foo value" (c/get-value cache-b :foo)))
         (time-keeper/advance-time! (inc hash-timeout))
         (is (nil? (c/get-value cache-b :foo))))

       (testing "The other cache keys should not be effected"
         (su/assert-values-in-caches caches {:bar "bar value"}))

       (testing "Keys after change should be correct"
         (su/assert-cache-keys [:foo :bar] cache-a)
         (su/assert-cache-keys [:bar] cache-b)))

     (testing "Change a value in the other cache"
       (c/set-value cache-b :foo "another foo value")

       (testing "The value is retrievable in the same cache"
         (is (= "another foo value" (c/get-value cache-b :foo))))

       (testing "Other cache still has the old value until time passes"
         (is (= "new foo value" (c/get-value cache-a :foo)))
         (time-keeper/advance-time! (inc hash-timeout))
         (is (nil? (c/get-value cache-a :foo))))

       (testing "The other cache keys should not be effected"
         (su/assert-values-in-caches caches {:bar "bar value"}))

       (testing "Keys after change should be correct"
         (su/assert-cache-keys [:bar] cache-a)
         (su/assert-cache-keys [:foo :bar] cache-b))))))

(defn clear-cache-test
  [cache-a cache-b hash-timeout]
  (time-keeper/with-frozen-time
   (let [caches [cache-a cache-b]
         initial-values {:foo "foo value" :bar "bar value"}]
     (su/put-values-in-caches caches initial-values)

     (c/reset cache-a)
     (is (empty? (c/get-keys cache-a)))

     (testing "Other cache get keys before hash timeout"
       (is (= #{:foo :bar} (set (c/get-keys cache-b)))))

     (testing "Other cache get keys after hash timeout"
       (time-keeper/advance-time! (inc hash-timeout))
       (is (empty? (c/get-keys cache-b))))

     (su/assert-values-in-caches caches {:foo nil :bar nil}))))

(defn get-value-with-lookup-test
  "This tests that get value with a lookup function will perform correctly. The names of objects in
  the test were chosen based on the use case for adding the consistent cache. They represent an in
  memory version of the real thing"
  [indexer1-cache indexer2-cache hash-timeout]
  (time-keeper/with-frozen-time
   (let [;; Represents ECHO's storage of ACLs. Acls here are just a list of symbols
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
         (time-keeper/advance-time! (inc hash-timeout))
         (assert-acls-from-cache [:acl1 :acl2 :acl3]))

       (testing "Manually updated one cache"
         (swap! echo-acls-atom conj :acl4)
         (c/set-value indexer2-cache :acls (lookup-fn))
         (time-keeper/advance-time! (inc hash-timeout))
         (assert-acls-from-cache [:acl1 :acl2 :acl3 :acl4])))

     (testing "Do not store a key with a nil value when lookup function does not find the value"
       (is (nil? (c/get-value indexer1-cache :unknown-key (constantly nil))))
       (su/assert-cache-keys [:acls] indexer1-cache)))))

(def ^:private cache-test-fns
  "Defines the set of test functions that check a cache implementation"
  [#'basic-consistency-test
   #'clear-cache-test
   #'get-value-with-lookup-test])

(defn assert-consistent-cache
  "Checks a consistent cache implementation to make sure it behaves as expected. Two caches should
  be passed in that use a common hash cache."
  [cache-a cache-b hash-timeout]
  (doseq [test-fn-var cache-test-fns]
    (c/reset cache-a)
    (c/reset cache-b)
    (testing (:name (meta test-fn-var))
      ((var-get test-fn-var) cache-a cache-b hash-timeout))))



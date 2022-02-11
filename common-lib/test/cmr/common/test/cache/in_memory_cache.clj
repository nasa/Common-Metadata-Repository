(ns cmr.common.test.cache.in-memory-cache
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.util :refer [are3]]))

(deftest memory-cache-functions-as-cache-test
  (cache-spec/assert-cache (mem-cache/create-in-memory-cache)))

(defn lru-cache-with
  [initial-value threshold]
  (mem-cache/create-in-memory-cache
    :lru
    initial-value
    {:threshold threshold}))

(deftest hit-and-miss-test
  (testing "cache, hit, miss, and reset without a lookup fn"
    (testing "value retrieval"
      (let [cache (lru-cache-with {:foo 1 :bar 2} 2)]
        (is (= 1 (c/get-value cache :foo)))
        (is (= 2 (c/get-value cache :bar)))
        (is (nil? (c/get-value cache :charlie)))))

    (testing "least recently stored is pushed out"
      (let [cache (lru-cache-with {} 2)]
        (c/set-value cache :foo 1)
        (c/set-value cache :bar 2)
        (c/set-value cache :charlie 3)
        (is (= [:bar :charlie] (sort (c/get-keys cache))))))

    (testing "misses will not push out other keys"
      (let [cache (lru-cache-with {:foo 1 :bar 2} 2)]
        ;; Cache misses
        (is (nil? (c/get-value cache :foo1)))
        (is (nil? (c/get-value cache :foo2)))

        (is (= [:bar :foo] (sort (c/get-keys cache))))))

    (testing "A cache hit will make the key be kept"
      (let [cache (lru-cache-with {:foo 1 :bar 2} 2)]
        ;; hit on bar
        (c/get-value cache :bar)
        ;; add a new key
        (c/set-value cache :charlie 3)
        ;; bar is still present.
        (is (= 2 (c/get-value cache :bar)))
        ;; foo is not present
        (is (nil? (c/get-value cache :foo))))

      (let [cache (lru-cache-with {:foo 1 :bar 2} 2)]
        ;; hit on foo
        (c/get-value cache :foo)
        ;; add a new key
        (c/set-value cache :charlie 3)
        ;; foo is still present.
        (is (= 1 (c/get-value cache :foo)))
        ;; bar is not present
        (is (nil? (c/get-value cache :bar)))))))

(deftest cache-size-test
  (let [in-mem-cache (mem-cache/create-in-memory-cache)]
    (testing "An empty cache has no size"
      (is (zero? (c/cache-size in-mem-cache))))

    (are3 [val expected-size]
      (do (c/reset in-mem-cache)
          (c/set-value in-mem-cache :key val)
          (is (= expected-size (c/cache-size in-mem-cache))))

      "Integer"
      (int 1024) java.lang.Integer/SIZE

      "clojure default Long"
      1024 java.lang.Long/SIZE

      "Double"
      1024.0 java.lang.Double/SIZE

      "String"
      "a string" 8

      "empty collection"
      [] 2

      "empty map"
      {} 2)))

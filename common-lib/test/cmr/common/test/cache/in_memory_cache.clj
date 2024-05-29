(ns cmr.common.test.cache.in-memory-cache
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [cmr.common.cache :as c]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.cache-spec :as cache-spec]
            [cmr.common.util :refer [are3 string->lz4-bytes]]))

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

(def ^:private umm-json (slurp (io/file (io/resource "test-data/in_memory_cache.json"))))

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

      "Long"
      1024 java.lang.Long/SIZE

      "Double"
      1024.0 java.lang.Double/SIZE

      "String"
      "a string" 8

      "keyword"
      :key-as-value 12

      "empty collection"
      [] 0

      "collection with entries"
      ["a" "b" "c"] 3

      "empty map"
      {} 0

      "map with data (simple)"
      {:a 1} 65

      "map with data"
      {:a "foo" :c 3} 69

      "umm_json"
      umm-json
      1083

      "umm_json"
      {:granule-a umm-json}
      1092

      "list of values"
      {:prov [umm-json]}
      1087

      "nested maps"
      {:prov {:gran umm-json}}
      1091

      "nested maps with lists"
      {:prov {:gran [umm-json]}}
      1091

      "nested maps with lists"
      {:prov {:coll {:grans [umm-json]
                     :other :info}}}
      1105

      "compressed strings"
      (string->lz4-bytes umm-json)
      836

      "clojure.lang.PersistentArrayMap as key"
      {(array-map [1 2] [3 4 5]) :array-map}
      329

      "map keyword->keyword"
      {:a :b}
      2

      "map string->string"
      {"c" "d"}
      2

      "clojure.lang.PersistentVector as key"
      {(vec [1]) :bar}
      67)))

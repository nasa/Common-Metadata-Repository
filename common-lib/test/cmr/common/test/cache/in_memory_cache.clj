(ns cmr.common.test.cache.in-memory-cache
  (:require [clojure.test :refer :all]
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

(def ^:private umm-json "{\"RelatedUrls\":[{\"URL\":\"opendap-replace@example.com\",\"Type\":\"USE SERVICE API\",\"Subtype\":\"OPENDAP DATA\"}],\"SpatialExtent\":{\"HorizontalSpatialDomain\":{\"Geometry\":{\"BoundingRectangles\":[{\"WestBoundingCoordinate\":-180.0,\"EastBoundingCoordinate\":180.0,\"NorthBoundingCoordinate\":90.0,\"SouthBoundingCoordinate\":-60.0}]}}},\"ProviderDates\":[{\"Date\":\"2018-02-06T19:13:22.000Z\",\"Type\":\"Insert\"},{\"Date\":\"2018-02-06T19:13:22.000Z\",\"Type\":\"Update\"}],\"CollectionReference\":{\"ShortName\":\"GLDAS_CLSM025_D\",\"Version\":\"2.0\"},\"DataGranule\":{\"DayNightFlag\":\"Unspecified\",\"Identifiers\":[{\"Identifier\":\"GLDAS_CLSM025_D.A19480101.020.nc4\",\"IdentifierType\":\"ProducerGranuleId\"}],\"ProductionDateTime\":\"2018-02-06T19:13:22.000Z\",\"ArchiveAndDistributionInformation\":[{\"Name\":\"Not provided\",\"Size\":24.7237091064453,\"SizeUnit\":\"MB\"}]},\"TemporalExtent\":{\"RangeDateTime\":{\"BeginningDateTime\":\"1948-01-01T00:00:00.000Z\",\"EndingDateTime\":\"1948-01-01T23:59:59.000Z\"}},\"GranuleUR\":\"Bulk Gran Replace\",\"MetadataSpecification\":{\"URL\":\"https://cdn.earthdata.nasa.gov/umm/granule/v1.6.4\",\"Name\":\"UMM-G\",\"Version\":\"1.6.4\"}}")

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

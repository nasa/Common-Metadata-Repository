(ns cmr.redis-utils.test.test-redis-cache
  "Namespace to test redis cache."
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [cmr.common.cache :as cache]
   [cmr.common.hash-cache :as h-cache]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis :as redis :refer [wcar*]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [taoensso.carmine :as carmine]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest testing-checking-if-redis-is-setup
  (let [key :testing-key-exists
        rcache (redis-cache/create-redis-cache {:keys-to-track [key]
                                                :read-connection (redis-config/redis-read-conn-opts)
                                                :primary-connection (redis-config/redis-conn-opts)})]
    (cache/reset rcache)
    (testing "cache key does not exist"
      (is (= false (cache/key-exists rcache key))))
    (testing "cache key does exist"
      (cache/set-value rcache key "some value")
      (is (cache/key-exists rcache key)))))

(deftest test-redis-cache-with-expire
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache {:ttl 10000
                                                  :read-connection (redis-config/redis-read-conn-opts)
                                                  :primary-connection (redis-config/redis-conn-opts)})
          s-key (redis-cache/serialize "test")]
      (cache/set-value rcache "test" "expire")
      (is (= "expire"
             (cache/get-value rcache "test")))
      (is (> (wcar* s-key true (redis-config/redis-conn-opts) (carmine/ttl s-key)) 0)))))

(deftest test-redis-cache-with-persistance
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache {:read-connection (redis-config/redis-read-conn-opts)
                                                  :primary-connection (redis-config/redis-conn-opts)})
          s-key (redis-cache/serialize "test")]
      (cache/set-value rcache "test" "persist")
      (is (= "persist"
             (cache/get-value rcache "test")))
      (is (= -1
             (wcar* s-key true (redis-config/redis-conn-opts) (carmine/ttl s-key)))))))

(deftest test-get-keys
  (wcar* "get-keys-pattern#-test1" false (redis-config/redis-conn-opts) (carmine/set "get-keys-pattern#-test1" "test1"))
  (wcar* "get-keys-pattern#-test2" false (redis-config/redis-conn-opts) (carmine/set "get-keys-pattern#-test2" "test2"))
  (testing "Get keys less than max scan keys..."
    (is (= 2 (count (redis/get-keys "get-keys-pattern#-*" (redis-config/redis-conn-opts))))))
  (testing "Get keys greater than max scan keys..."
    (doseq [x (range (* 2 (redis-config/redis-max-scan-keys)))]
      (wcar* (str "get-keys-pattern-2#-" x) false (redis-config/redis-conn-opts) (carmine/set (str "get-keys-pattern-2#-" x) x)))
    (is (= (redis-config/redis-max-scan-keys)
           (count (redis/get-keys "get-keys-pattern-2#-*" (redis-config/redis-conn-opts)))))))

(def field-value-map
  {"C1200000001-PROV1"
   {:concept-id "C1200000001-PROV1",
    :revision-id 4,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>Mapping...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{      \"DataLanguage\" : \"Engli..."}
   "C1200000002-PROV1"
   {:concept-id "C1200000002-PROV1",
    :revision-id 4,
    :native-format {:format :umm-json, :version "1.17.3"},
    :echo10 "<Collection><ShortName>SWOT_L2...",
    :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
    :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
    :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."}})

(def single-field-value-map
  {"C1200000003-PROV1" {:concept-id "C1200000003-PROV1",
                        :revision-id 4,
                        :native-format {:format :umm-json, :version "1.17.3"},
                        :echo10 "<Collection><ShortName>SWOT_L2...",
                        :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
                        :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
                        :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
                        :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."}})

(deftest redis-hash-cash-test
  (let [cache-key :test-hash-cache
        rhcache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]
                                                           :read-connection (redis-config/redis-read-conn-opts)
                                                           :primary-connection (redis-config/redis-conn-opts)})]

    (testing "Clearing the cache with cache-key"
      (h-cache/set-value rhcache cache-key "C1200000003-PROV1" (get single-field-value-map "C1200000003-PROV1"))
      (h-cache/reset rhcache cache-key)
      (is (= nil (h-cache/get-map rhcache cache-key))))

    (testing "Clearing the cache with out cache-key - its already defined when cache is created."
      (h-cache/set-value rhcache cache-key "C1200000003-PROV1" (get single-field-value-map "C1200000003-PROV1"))
      (h-cache/reset rhcache)
      (is (= nil (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting an empty hashmap back"
      (is (= nil
           (h-cache/get-map rhcache cache-key))))

    (testing "The cache is empty because it hasn't been populated"
      (is (= false (h-cache/key-exists rhcache cache-key))))

    (h-cache/set-value rhcache cache-key "C1200000003-PROV1" {:concept-id "C1200000003-PROV1",
                                                              :revision-id 4,
                                                              :native-format {:format :umm-json, :version "1.17.3"},
                                                              :echo10 "<Collection><ShortName>SWOT_L2...",
                                                              :dif "<DIF xmlns=\"http://gcmd.gsfc.n...",
                                                              :dif10 "<DIF xmlns=\"http://gcmd.gsfc.n...",
                                                              :iso19115 "<gmi:MI_Metadata xmlns:xs=\"htt...",
                                                              :umm-json_1.17.3 "{  \"AdditionalAttributes\" : [ ..."})
    (testing "The cache key exists in redis."
      (is (= true (h-cache/key-exists rhcache cache-key))))

    (testing "Testing getting the entire hashmap back"
      (is (= single-field-value-map
             (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting the fields of the hashmap back"
      (is (= ["C1200000003-PROV1"]
             (h-cache/get-keys rhcache cache-key))))

    (testing "Testing getting a value back"
      (is (= (get single-field-value-map "C1200000003-PROV1")
             (h-cache/get-value rhcache cache-key "C1200000003-PROV1"))))

    (testing "Testing getting getting multiple values back from a list."
      (is (= (list (get single-field-value-map "C1200000003-PROV1"))
             (h-cache/get-values rhcache cache-key '("C1200000003-PROV1")))))

    (testing "Testing ingesting a field value map and pulling out the hash map"
      (h-cache/set-values rhcache cache-key field-value-map)
      (is (= (merge field-value-map single-field-value-map)
             (h-cache/get-map rhcache cache-key))))

    (testing "Testing getting all of the fields from the hash cache."
      (is (= (set ["C1200000001-PROV1" "C1200000002-PROV1" "C1200000003-PROV1"])
             (set (h-cache/get-keys rhcache cache-key)))))

    (testing "Testing getting a value back from a bigger hash map."
      (is (= (get single-field-value-map "C1200000003-PROV1")
             (h-cache/get-value rhcache cache-key "C1200000003-PROV1"))))

    (testing "Testing getting multiple values back."
      (is (= (let [full-map (merge field-value-map single-field-value-map)]
               (set [(get full-map "C1200000003-PROV1") (get full-map "C1200000002-PROV1")]))
             (set (h-cache/get-values rhcache cache-key '("C1200000003-PROV1" "C1200000002-PROV1"))))))

    (testing "Testing getting size back from redis."
      (is (= java.lang.Long (type (h-cache/cache-size rhcache cache-key)))))

    (testing "Testing removing a field from redis."
      (is (= 1 (h-cache/remove-value rhcache cache-key "C1200000003-PROV1"))))))

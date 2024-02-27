(ns cmr.redis-utils.test.test-redis-cache
  "Namespace to test redis cache."
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.redis :as redis :refer [wcar*]]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.redis-utils.config :as config]
   [taoensso.carmine :as carmine]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest testing-checking-if-redis-is-setup
  (let [key :testing-key-exists
        rcache (redis-cache/create-redis-cache {:keys-to-track [key]})]
    (cache/reset rcache)
    (testing "cache key does not exist"
      (is (= false (cache/key-exists rcache key))))
    (testing "cache key does exist"
      (cache/set-value rcache key "some value")
      (is (cache/key-exists rcache key)))))

(deftest test-redis-cache-with-expire
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache {:ttl 10000})]
      (cache/set-value rcache "test" "expire")
      (is (= "expire"
             (cache/get-value rcache "test")))
      (is (> (wcar* (carmine/ttl (redis-cache/serialize "test"))) 0)))))

(deftest test-redis-cache-with-persistance
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache)]
      (cache/set-value rcache "test" "persist")
      (is (= "persist"
             (cache/get-value rcache "test")))
      (is (= -1
             (wcar* (carmine/ttl (redis-cache/serialize "test"))))))))

(deftest test-get-keys
  (wcar* (carmine/set "get-keys-pattern#-test1" "test1"))
  (wcar* (carmine/set "get-keys-pattern#-test2" "test2"))
  (testing "Get keys less than max scan keys..."
    (is (= 2 (count (redis/get-keys "get-keys-pattern#-*")))))
  (testing "Get keys greater than max scan keys..."
    (doseq [x (range (* 2 (config/redis-max-scan-keys)))]
      (wcar* (carmine/set (str "get-keys-pattern-2#-" x) x)))
    (is (= (config/redis-max-scan-keys)
           (count (redis/get-keys "get-keys-pattern-2#-*"))))))

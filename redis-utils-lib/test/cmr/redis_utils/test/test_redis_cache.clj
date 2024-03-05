(ns cmr.redis-utils.test.test-redis-cache
  "Namespace to test redis cache."
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.common.hash-cache :as h-cache]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.redis :as redis :refer [wcar*]]
   [cmr.redis-utils.redis-cache :as redis-cache]
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

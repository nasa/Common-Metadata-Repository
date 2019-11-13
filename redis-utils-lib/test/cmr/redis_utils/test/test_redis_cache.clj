(ns cmr.redis-utils.test.test-redis-cache
  "Namespace to test redis cache."
  (:require
   [clojure.test :refer :all]
   [cmr.common.cache :as cache]
   [cmr.redis-utils.redis-cache :as redis-cache]
   [cmr.redis-utils.test.test-util :as test-util]
   [cmr.redis-utils.config :as config]
   [taoensso.carmine :as carmine :refer [wcar]]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest test-redis-cache-with-expire
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache {:ttl 10000})]
      (cache/set-value rcache "test" "expire")
      (is (= "expire"
             (cache/get-value rcache "test")))
      (is (> (wcar {} (carmine/ttl (redis-cache/serialize "test"))) 0)))))

(deftest test-redis-cache-with-persistance
  (testing "Redis cache with default timeout..."
    (let [rcache (redis-cache/create-redis-cache)]
      (cache/set-value rcache "test" "persist")
      (is (= "persist"
             (cache/get-value rcache "test")))
      (is (= (wcar {} (carmine/ttl (redis-cache/serialize "test"))) -1)))))

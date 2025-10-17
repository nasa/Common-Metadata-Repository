(ns cmr.redis-utils.test.test-redis-hash-cache-errors
  "Namespace to test redis cache."
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.hash-cache :as h-cache]
   [cmr.redis-utils.redis-hash-cache :as redis-hash-cache]))

(deftest redis-hash-cache-errors-test
  ;; This is to test all redis hash cache connection errors
  (let [cache-key :test-hash-cache
        rhcache (redis-hash-cache/create-redis-hash-cache {:keys-to-track [cache-key]})]

    (testing "key-exists returns nil when redis connection is not setup properly"
      (is (thrown? Exception (h-cache/key-exists rhcache cache-key))))))

(ns cmr.redis-utils.test.test-embedded-server
  "Namespace to test embedded redis server"
  (:require
   [clojure.test :refer :all]
   [cmr.redis-utils.test.test-util :as test-util]
   [taoensso.carmine :as carmine :refer [wcar]]))

(use-fixtures :once test-util/embedded-redis-server-fixture)

(deftest test-basic-redis
  (testing "Able to reach redis server..."
    (is (= "PONG" (wcar {} (carmine/parse-raw (carmine/ping))))))
  (testing "Basic key insertion and retrieval..."
    (is (= "OK" (wcar {} (carmine/set "test" "insertion"))))
    (is (= "insertion" (wcar {} (carmine/get "test"))))))

(ns cmr.redis-utils.test.test-embedded-server
  "Namespace to test embedded redis server"
  (:require
   [clojure.test :refer :all]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.redis-utils.embedded-redis-server :as embedded-redis-server]
   [taoensso.carmine :as carmine :refer [wcar]]))

(defn- embedded-redis-server-fixture
  [f]
  (try
    ;; Check if server is already running.
    (wcar {} (carmine/ping))
    (f)
    (catch Exception _
      (let [redis-server (embedded-redis-server/create-redis-server)
            started-redis-server (lifecycle/start redis-server nil)]
        (f)
        (lifecycle/stop started-redis-server nil)))))

(use-fixtures :once embedded-redis-server-fixture)

(deftest test-basic-redis
  (testing "Able to reach redis server..."
    (is (= "PONG" (wcar {} (carmine/parse-raw (carmine/ping))))))
  (testing "Basic key insertion and retrieval..."
    (is (= "OK" (wcar {} (carmine/set "test" "insertion"))))
    (is (= "insertion" (wcar {} (carmine/get "test"))))))

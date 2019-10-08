(ns cmr.redis-utils.test.test-embedded-server
  "Namespace to test embedded redis server"
  (:require
   [clojure.test :refer :all]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.redis-utils.embedded-redis-server :as embedded-redis-server]
   [taoensso.carmine :as carmine :refer [wcar]]))

(defn- embedded-redis-server-fixture
  [f]
  (let [redis-server (embedded-redis-server/create-redis-server 6379)
        started-redis-server (lifecycle/start redis-server nil)]
    (f)
    (lifecycle/stop started-redis-server nil)))

(use-fixtures :once embedded-redis-server-fixture)

(def ^:private redis-connection
  {:pool {}
   :spec {:host "localhost"
          :port 6379}})

(deftest test-basic-redis
  (testing "Able to reach redis server..."
    (is (= "PONG" (wcar redis-connection (carmine/ping)))))
  (testing "Basic key insertion and retrieval..."
    (is (= "OK" (wcar redis-connection (carmine/set "test" "insertion"))))
    (is (= "insertion" (wcar redis-connection (carmine/get "test"))))))

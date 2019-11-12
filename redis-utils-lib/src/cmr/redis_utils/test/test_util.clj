(ns cmr.redis-utils.test.test-util
  "Namespace to test embedded redis server"
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.redis-utils.embedded-redis-server :as embedded-redis-server]
   [taoensso.carmine :as carmine :refer [wcar]]))

(defn embedded-redis-server-fixture
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


(defn reset-redis-fixture
  [f]
  (wcar {} (carmine/flushall))
  (f))

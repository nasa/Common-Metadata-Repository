(ns cmr.redis-utils.embedded-redis-server
  "Used to run a Redis server inside a docker container."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug error]]
   [cmr.redis-utils.config :as redis-config])
  (:import
   (org.testcontainers.containers GenericContainer Network)))

(def ^:private redis-image
  "Official redis image."
  "docker.io/redis:7-bullseye")

(defn- build-redis
  "Setup redis docker image"
  [network]
  (doto (GenericContainer. redis-image)
    (.withExposedPorts(int 6379))
    (.withNetwork network)))

(defrecord RedisServer
    [
     opts]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [network (Network/newNetwork)
          ^GenericContainer redis (build-redis network)]
      (try
        (.start redis)
        (redis-config/set-redis-port! (.getMappedPort redis 6379))
        (debug "Started redis with port " (.getMappedPort redis 6379))
        (assoc this :redis redis)
        (catch Exception e
          (error "Redis failed to start.")
          (debug "Dumping logs:\n" (.getLogs redis))
          (throw (ex-info "Redis failure" {:exception e}))))))

  (stop
    [this system]
    (when-let [redis (:redis this)]
      (.stop redis))))

(defn create-redis-server
  ([]
   (create-redis-server {}))
  ([opts]
   (->RedisServer opts)))

(comment

  (def server (create-redis-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

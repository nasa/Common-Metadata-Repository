(ns cmr.redis-utils.embedded-redis-server
  "Used to run a Redis server inside a docker container."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug error]])
  (:import
   (org.testcontainers.containers FixedHostPortGenericContainer)))

(def REDIS_DEFAULT_PORT 6379)

(def ^:private redis-image
  "Official redis image."
  "docker.io/redis:7-bullseye")

(defn- build-redis
  "Setup redis docker image"
  [http-port]
  (doto (FixedHostPortGenericContainer. redis-image)
    (.withFixedExposedPort (int http-port) REDIS_DEFAULT_PORT)))

(defrecord RedisServer
           [
            http-port
            opts]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting Redis server on port" http-port)
    (let [^FixedHostPortGenericContainer redis (build-redis http-port)]
      (try
        (.start redis)
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
   (create-redis-server REDIS_DEFAULT_PORT))
  ([http-port]
   (create-redis-server http-port {}))
  ([http-port opts]
   (->RedisServer http-port opts)))

(comment

  (def server (create-redis-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

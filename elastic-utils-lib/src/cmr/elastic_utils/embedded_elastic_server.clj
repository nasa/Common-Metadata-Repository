(ns cmr.elastic-utils.embedded-elastic-server
  "Used to run an in memory Elasticsearch server."
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util])
  (:import
   (java.time Duration)
   (org.testcontainers.containers FixedHostPortGenericContainer)
   (org.testcontainers.containers.wait Wait)
   (org.testcontainers.images.builder ImageFromDockerfile)))

(def ^:private elasticsearch-official-docker-image
  "Official docker image."
  "docker.elastic.co/elasticsearch/elasticsearch")

(def ^:private elasticsearch-version
  "Elasticsearch version to use."
  "7.5.2")

(defn- build-node
  "Build cluster node with settings. The elasticsearch server is actually
  started on the default port 9200/9300. Only changing host port mapping. Args:

  http-port -> http port host will reach elasticsearch container on.
  opts ->
       data-dir -> Data directory to mount. None is default.
       dockerfile -> Dockerfile on classpath to use. Uses default image otherwise.
       log-level -> log level to use. info is default.
       image-cfg -> Map of files on the classpath needed to build the docker
        image specified. Must specify dockerfile with key \"Dockerfile\" otherwise
        uses default image. See:
        https://www.testcontainers.org/features/creating_images/"
  ([http-port]
   (build-node http-port {}))
  ([http-port opts]
   (let [{:keys [data-dir image-cfg log-level]} opts
         image (if (get image-cfg "Dockerfile")
                 (let [docker-image (ImageFromDockerfile.)]
                   (doseq [[k v] image-cfg]
                     (.withFileFromClasspath docker-image k v))
                   (.get docker-image))
                 (format "%s:%s"
                        elasticsearch-official-docker-image
                        elasticsearch-version))
         container (FixedHostPortGenericContainer. image)]
     ;; This will cause a pretty big performance hit locally if you provide data-dir.
     ;; You would probably be better off just connecting to the docker machine.
     (when data-dir
       (.withFileSystemBind container data-dir "/usr/share/elasticsearch/data"))
     (when log-level
       (.withEnv container "logger.level" (name log-level)))
     (doto container
           (.withEnv "discovery.type" "single-node")
           (.withEnv "indices.breaker.total.use_real_memory" "false")
           (.withEnv "node.name" "embedded-elastic")
           (.withFixedExposedPort (int http-port) 9200)
           (.withStartupTimeout (Duration/ofSeconds 120))
           (.waitingFor
            (.forStatusCode (Wait/forHttp "/_cat/health?v&pretty") 200))))))

(defrecord ElasticServer
  [
   http-port
   opts
   node]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting elastic server on port" http-port)
    (let [^FixedHostPortGenericContainer node (build-node http-port opts)]
      (try
        (.start node)
        (assoc this :node node)
        (catch Exception e
          (error "Container failed to start.")
          (debug "Dumping failed elasticsearch logs:\n" (.getLogs node))
          (throw e)))))
  (stop
    [this system]
    (when-let [^FixedHostPortGenericContainer node (:node this)]
      (do
        (.stop node)
        (when-let [data-dir (get-in this [:opts :data-dir])]
          (util/delete-recursively (:data-dir this)))))
    (assoc this :node nil)))

(defn create-server
  ([]
   (create-server 9200))
  ([http-port]
   (create-server http-port {}))
  ([http-port opts]
   (->ElasticServer http-port opts nil)))

(comment

  (def server (create-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

(ns cmr.elastic-utils.embedded-elastic-server
  "Used to run an in memory Elasticsearch server."
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util])
  (:import
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
  data-dir -> Data directory.
  dockerfile -> Dockerfile on classpath to use. nil to use default elastic image.
  log-level -> log level to use.
  classpath-files -> Map of files on the classpath needed to build the docker
       image specified in dockerfile. Template:
       {\"dockerfile-resource-name\" \"name-of-file-on-classpath\"}. See:
       https://www.testcontainers.org/features/creating_images/"
  [http-port data-dir dockerfile log-level classpath-files]
  (let [image (if dockerfile
                (let [docker-image (ImageFromDockerfile.)]
                  (.withFileFromClasspath docker-image "Dockerfile" dockerfile)
                  (doseq [[k v] classpath-files]
                    (.withFileFromClasspath docker-image k v))
                  (.get docker-image))
                (format "%s:%s"
                       elasticsearch-official-docker-image
                       elasticsearch-version))]
    (doto (FixedHostPortGenericContainer. image)
          (.withEnv "discovery.type" "single-node")
          (.withEnv "indices.breaker.total.use_real_memory" "false")
          (.withEnv "logger.level" (name log-level))
          (.withEnv "node.name" "embedded-elastic")
          (.withFileSystemBind data-dir "/usr/share/elasticsearch/data")
          (.withFixedExposedPort (int http-port) 9200)
          (.waitingFor
           (.forStatusCode (Wait/forHttp "/_cat/health?v&pretty") 200)))))

(defrecord ElasticServer
  [
   http-port
   data-dir
   dockerfile
   log-level
   classpath-files
   node]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting elastic server on port" http-port)
    (let [^FixedHostPortGenericContainer node (build-node http-port
                                                          data-dir
                                                          dockerfile
                                                          log-level
                                                          classpath-files)]
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
        (util/delete-recursively (:data-dir this))))
    (assoc this :node nil)))

(defn create-server
  ([]
   (create-server 9200 "data"))
  ([http-port data-dir]
   (create-server http-port data-dir nil))
  ([http-port data-dir dockerfile]
   (create-server http-port data-dir dockerfile "info"))
  ([http-port data-dir dockerfile log-level]
   (create-server http-port data-dir dockerfile log-level {}))
  ([http-port data-dir dockerfile log-level classpath-files]
   (->ElasticServer http-port data-dir dockerfile log-level classpath-files nil)))

(comment

  (def server (create-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

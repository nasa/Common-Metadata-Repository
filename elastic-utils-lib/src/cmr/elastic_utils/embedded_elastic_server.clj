(ns cmr.elastic-utils.embedded-elastic-server
  "Used to run an Elasticsearch server inside an embedded docker container."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util])
  (:import
   (java.time Duration)
   (org.testcontainers.containers FixedHostPortGenericContainer Network)
   (org.testcontainers.containers.wait.strategy Wait)
   (org.testcontainers.images.builder ImageFromDockerfile)))

(def ^:private elasticsearch-official-docker-image
  "Official docker image."
  "docker.elastic.co/elasticsearch/elasticsearch:7.16.2")

(def ^:private kibana-official-docker-image
  "Official kibana docker image."
  "docker.elastic.co/kibana/kibana:7.16.2")

(defn- build-kibana
  "Build kibana in an embedded docker."
  [http-port network]
  (doto (FixedHostPortGenericContainer. kibana-official-docker-image)
        (.withFixedExposedPort (int http-port) 5601)
        (.withNetwork network)))

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
        https://www.testcontainers.org/features/creating_images/
       kibana-port -> if provided will also bring up a kibana container to use with the
        new elasticsearch node."
  ([http-port]
   (build-node http-port {}))
  ([http-port opts]
   (let [{:keys [data-dir image-cfg log-level kibana-port]} opts
         image (if (get image-cfg "Dockerfile")
                 (let [docker-image (ImageFromDockerfile.)]
                   (doseq [[k v] image-cfg]
                     (.withFileFromClasspath docker-image k v))
                   (.get docker-image))
                 elasticsearch-official-docker-image)
         container (FixedHostPortGenericContainer. image)
         network (Network/newNetwork)
         kibana (when kibana-port
                  (build-kibana kibana-port network))]
     ;; This will cause a pretty big performance hit locally if you provide data-dir.
     ;; You would probably be better off just connecting to the docker machine.
     (when data-dir
       (.withFileSystemBind container data-dir "/usr/share/elasticsearch/data"))
     (when log-level
       (.withEnv container "logger.level" (name log-level)))
     (doto container
           (.withEnv "indices.breaker.total.use_real_memory" "false")
           (.withEnv "node.name" "embedded-elastic")
           (.withNetwork network)
           (.withNetworkAliases (into-array String ["elasticsearch"]))
           (.withFixedExposedPort (int http-port) 9200)
           (.withStartupTimeout (Duration/ofSeconds 120))
           (.waitingFor
            (.forStatusCode (Wait/forHttp "/_cat/health?v&pretty") 200)))
     {:elasticsearch container
      :kibana kibana})))

(defrecord ElasticServer
    [
     http-port
     opts
     node]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting elastic server on port" http-port)
    (let [containers (build-node http-port opts)
          ^FixedHostPortGenericContainer node (:elasticsearch containers)
          ^FixedHostPortGenericContainer kibana (:kibana containers)]
      (try
        (.start node)
        (when kibana
          (debug "Starting kibana server on port" (:kibana-port opts))
          (.start kibana))
        (assoc this :containers containers)
        (catch Exception e
          (error "Container(s) failed to start.")
          (debug "Dumping elasticsearch logs:\n" (.getLogs node))
          (when kibana
            (debug "Dumping kibana logs:\n" (.getLogs kibana)))
          (throw e)))))
  (stop
    [this system]
    (let [containers (:containers this)
          ^FixedHostPortGenericContainer node (:elasticsearch containers)
          ^FixedHostPortGenericContainer kibana (:kibana containers)]
      (.stop node)
      (when-let [data-dir (get-in this [:opts :data-dir])]
        (util/delete-recursively (:data-dir this)))
      (when kibana
        (.stop kibana)))
    (assoc this :containers nil)))

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

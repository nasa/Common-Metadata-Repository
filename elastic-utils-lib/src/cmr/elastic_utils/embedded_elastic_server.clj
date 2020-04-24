(ns cmr.elastic-utils.embedded-elastic-server
  "Used to run an in memory Elasticsearch server."
  (:require
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util])
  (:import
   (com.github.dockerjava.api.model ExposedPort Ports PortBinding Ports$Binding)
   (org.testcontainers.containers FixedHostPortGenericContainer)
   (org.testcontainers.containers.output ToStringConsumer)
   (org.testcontainers.containers.wait Wait)
   (org.testcontainers.utility MountableFile)))

(def ^:private elasticsearch-official-docker-image
  "Official docker image."
  "docker.elastic.co/elasticsearch/elasticsearch")

(def ^:private elasticsearch-version
  "Elasticsearch version to use."
  "7.5.2")

(def ^:private embedded-security-policy
  "Embedded security policy file."
  (io/file (io/resource "embedded-security.policy")))

(def ^:private embedded-jvm-opts
  "Embedded jvm options."
  (io/file (io/resource "embedded-jvm-opts.options")))

(defn- build-node
  "Build cluster node with given settings. The elasticsearch server is actually
  started on the default port 9200/9300. Only changing host port mapping."
  [http-port transport-port data-dir log-level plugin-dir es-libs-dir]
  (let [^FixedHostPortGenericContainer node
        (new FixedHostPortGenericContainer
             (format "%s:%s"
                     elasticsearch-official-docker-image
                     elasticsearch-version))]
    (doto node
          (.withEnv "discovery.type" "single-node")
          (.withEnv "indices.breaker.total.use_real_memory" "false")
          (.withEnv "logger.level" log-level)
          (.withEnv "node.name" "embedded-elastic")
          (.withFileSystemBind plugin-dir "/usr/share/elasticsearch/plugins")
          (.withFileSystemBind data-dir "/usr/share/elasticsearch/data")
          (.withFixedExposedPort (int http-port) 9200)
          (.waitingFor
           (.forStatusCode (Wait/forHttp "/_cat/health?v&pretty") 200)))
    ;; Copy security policy
    (.withCopyFileToContainer
     node
     (MountableFile/forHostPath (.getPath embedded-security-policy))
     (str "/usr/share/elasticsearch/" (.getName embedded-security-policy)))
    ;; Copy jvm opts
    (.withCopyFileToContainer
     node
     (MountableFile/forHostPath (.getPath embedded-jvm-opts))
     "/usr/share/elasticsearch/config/jvm.options")
    ;; Copy additional libs
    (doseq [f (-> es-libs-dir io/file file-seq)
            :when (.isFile f)
            :let [file-name (.getName f)]]
      (debug "Adding lib" file-name)
      (.withCopyFileToContainer
       node
       (MountableFile/forHostPath (.getPath f))
       (str "/usr/share/elasticsearch/lib/" file-name)))
    node))

(defrecord ElasticServer
  [
   http-port
   transport-port
   data-dir
   log-level
   plugin-dir
   es-libs-dir
   node]

  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting elastic server on port" http-port)
    (let [^FixedHostPortGenericContainer node (build-node http-port
                                                          transport-port
                                                          data-dir
                                                          log-level
                                                          plugin-dir
                                                          es-libs-dir)]
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
   (create-server 9200 9300 "data"))
  ([http-port transport-port data-dir]
   (create-server http-port transport-port data-dir "info"))
  ([http-port transport-port data-dir log-level]
   (create-server http-port transport-port data-dir log-level "plugins"))
  ([http-port transport-port data-dir log-level plugin-dir]
   (create-server http-port transport-port data-dir log-level plugin-dir "es_libs"))
  ([http-port transport-port data-dir log-level plugin-dir es-libs-dir]
   (->ElasticServer http-port transport-port data-dir log-level plugin-dir es-libs-dir nil)))

(comment

  (def server (create-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

(ns cmr.elastic-utils.embedded-elastic-server
  "Used to run an in memory Elasticsearch server."
  (:require
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.util :as util])
  (:import
   (org.codelibs.elasticsearch.runner ElasticsearchClusterRunner
                                      ElasticsearchClusterRunner$Builder)
   (org.elasticsearch.common.settings Settings 
                                      Settings$Builder)))

(defn- create-settings
  "Implements the Builder interface in order to configure the elasticsearch settings."
  [http-port transport-port data-dir]
  (proxy [ElasticsearchClusterRunner$Builder] []
    (build [index ^Settings$Builder settingsBuilder]
      (.. settingsBuilder
          (put "node.name" "embedded-elastic")
          (put "path.data" data-dir)
          (put "http.port" (str http-port))
          (put "transport.tcp.port" (str transport-port))
          (put "index.store.type" "memory")))))

(defn build-node
  "Build cluster node to run on http-port and transport-port with data-dir."
  [http-port transport-port data-dir]
  (let [^ElasticsearchClusterRunner node (ElasticsearchClusterRunner.)]
    (.build
     (.onBuild node (create-settings http-port transport-port data-dir))
     (-> (ElasticsearchClusterRunner/newConfigs)
         (.numOfNode 1)
         (.useLogger)
         (.clusterName "cmr-embedded-elastic")))
    node))

(defrecord ElasticServer
  [
   http-port
   transport-port
   data-dir
   node]


  lifecycle/Lifecycle

  (start
    [this system]
    (debug "Starting elastic server on port" http-port)
    (let [^ElasticsearchClusterRunner node (build-node http-port transport-port data-dir)]
      ;; ensureYellow takes variable arguments. Will not recognize 0-arity call. Must pass array coerced to java.lang.String.
      (.ensureYellow node (into-array java.lang.String []))
      (assoc this :node node)))

  (stop
    [this system]
    (when-let [^ElasticsearchClusterRunner node (:node this)]
      (do
        (.close node)
        (.clean node)
        (util/delete-recursively (:data-dir this))))
    (assoc this :node nil)))

(defn create-server
  ([]
   (create-server 9200 9300 "data"))
  ([http-port transport-port data-dir]
   (->ElasticServer http-port transport-port data-dir nil)))

(comment

  (def server (create-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil)))

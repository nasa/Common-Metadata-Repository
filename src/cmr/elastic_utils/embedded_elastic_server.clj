(ns cmr.elastic-utils.embedded-elastic-server
  (:import (org.elasticsearch.common.settings
             ImmutableSettings
             ImmutableSettings$Builder)
           org.elasticsearch.common.logging.log4j.LogConfigurator
           org.elasticsearch.node.NodeBuilder
           org.elasticsearch.node.Node)
  (:require [cmr.common.lifecycle :as lifecycle]))


(defn- setup-logging
  "Sets up elastic search logging."
  [settings]
  (LogConfigurator/configure settings))

(defn- create-settings
  "Creates an Elastic Search Immutable Settings"
  [{:keys [http-port transport-port]}]
  (.. (ImmutableSettings/settingsBuilder)
      (put "node.name" "embedded-elastic")
      (put "http.port" (str http-port))
      (put "transport.tcp.port" (str transport-port))

      (put "index.store.type" "memory")
      build))

(defn- build-node
  "Creates the internal elastic search node that will run."
  [^ImmutableSettings node-settings]
  (let [^NodeBuilder builder (NodeBuilder/nodeBuilder)]
    (.. builder
        (settings node-settings)

        (clusterName "embedded-cluster")

        ;;Is the node going to be allowed to allocate data (shards) to it or not.
        (data true)

        ;;The node is local within a JVM. It will not try to connect to nodes outside
        (local true)

        ;; Starts the node
        node)))

(defrecord ElasticServer
  [
   http-port
   transport-port
   node
  ]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [node-settings (create-settings this)]
      (setup-logging node-settings)
      (assoc this :node (build-node node-settings))))

  (stop
    [this system]
    (when-let [^Node node (:node this)]
      (.close node))
    (assoc this :node nil)))

(defn create-server
  ([]
   (create-server 9200 9300))
  ([http-port transport-port]
   (->ElasticServer http-port transport-port nil)))


(comment

  (def server (create-server))

  (def started-server (lifecycle/start server nil))

  (def stopped-server (lifecycle/stop started-server nil))

)


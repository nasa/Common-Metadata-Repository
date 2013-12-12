(ns embedded-elastic-server
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
  [http-port transport-port]
  (.. (ImmutableSettings/settingsBuilder)
      (put "node.name" "repl-elastic")
      (put "path.conf" "dev")
      (put "http.port" (str http-port))
      (put "transport.tcp.port" (str transport-port))
      ; (put "index.store.type" "memory")
      build))

(defn- build-node
  "Creates the internal elastic search node that will run."
  [^ImmutableSettings node-settings]
  (let [^NodeBuilder builder (NodeBuilder/nodeBuilder)]
    (.. builder
        (settings node-settings)

        (clusterName "repl-cluster")

        ;;Is the node going to be allowed to allocate data (shards) to it or not.
        (data true)

        ;;The node is local within a JVM. It will not try to connect to nodes outside
        (local true)

        ;; Starts the node
        node)))

(defrecord ElasticServer
  [
   node
  ]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [{:keys [http-port transport-port]} (:config system)
          node-settings (create-settings http-port transport-port)]
      (setup-logging node-settings)
      (println "Starting elastic server")
      (assoc this :node (build-node node-settings))))

  (stop
    [this system]
    (when-let [^Node node (:node this)]
      (println "Stopping elastic server")
      (.close node))
    (assoc this :node nil)))

(defn create-server []
  (->ElasticServer nil))

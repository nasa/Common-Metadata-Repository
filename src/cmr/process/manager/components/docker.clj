(ns cmr.process.manager.components.docker
  (:require
    [cmr.process.manager.docker :as docker]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defn get-opts
  [system service-key]
  (get-in system [service-key :opts]))

(defn get-container-id
  [system service-key]
  (docker/read-container-id (get-opts system service-key)))

(defn get-container-data
  [system service-key]
  (docker/inspect (get-opts system service-key)))

(defn get-container-state
  [system service-key]
  (docker/state (get-opts system service-key)))

(defrecord DockerRunner [
  builder
  process-keyword
  opts-fn
  opts]
  component/Lifecycle

  (start [component]
    (let [cfg (builder (:process-keyword component))
          component (assoc component :config cfg)
          opts ((:opts-fn component) component)
          process-key (:process-keyword component)
          process-name (name process-key)]
      (log/infof "Starting %s docker component ..." process-name)
      (log/debug "Component keys:" (keys component))
      (log/trace "Built configuration:" cfg)
      (log/debug "Config:" (:config component))
      (log/debug "Docker options:" opts)
      (docker/pull opts)
      (docker/run opts)
      (assoc component :enabled true :opts opts)))

  (stop [component]
    (let [process-key (:process-keyword component)
          process-name (name process-key)]
      (log/debug "Process name:" process-name)
      (log/infof "Stopping %s docker component ..." process-name)
      (docker/stop (:opts component))
      component)))

(defn create-component
  ""
  [config-builder-fn process-keyword docker-opts-fn]
  (map->DockerRunner
    {:builder config-builder-fn
     :process-keyword process-keyword
     :opts-fn docker-opts-fn}))

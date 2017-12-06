(ns cmr.dev.env.manager.components.common.docker
  (:require
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.process.docker :as docker]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

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
      (if (config/service-enabled? component process-key)
        (do
          (docker/pull opts)
          (docker/run opts)
          (assoc component :enabled true :opts opts))
        (do
          (log/debugf (str "Docker service %s not enabled; "
                           "skipping component start-up.")
                      process-key)
          (assoc component :enabled false)))))

  (stop [component]
    (let [process-key (:process-keyword component)
          process-name (name process-key)]
      (log/debug "Process name:" process-name)
      (log/infof "Stopping %s docker component ..." process-name)
      (if (config/service-enabled? component process-key)
        (do
          (docker/stop (:opts component))
          component)
        (do
          (log/debugf (str "Docker service %s not enabled; "
                           "skipping component shut-down.")
                      process-key)
          component)))))

(defn create-runner-component
  ""
  [config-builder-fn process-keyword docker-opts-fn]
  (map->DockerRunner
    {:builder config-builder-fn
     :process-keyword process-keyword
     :opts-fn docker-opts-fn}))

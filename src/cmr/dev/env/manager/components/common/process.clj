(ns cmr.dev.env.manager.components.common.process
  (:require
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.process.core :as process]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defn get-process
  [system service-key]
  (get-in system [service-key :process-data]))

(defn get-process-id
  [system service-key]
  (process/get-pid (get-process system service-key)))

(defn get-process-descendants
  [system service-key]
  (process/get-descendants (get-process system service-key)))

(defrecord ProcessRunner [
  builder
  process-keyword
  process-data]
  component/Lifecycle

  (start [component]
    (let [cfg (builder (:process-keyword component))
          component (assoc component :config cfg)
          process-key (:process-keyword component)
          process-name (name process-key)]
      (log/infof "Starting %s component ..." process-name)
      (log/debug "Component keys:" (keys component))
      (log/trace "Built configuration:" cfg)
      (log/debug "Config:" (:config component))
      (if (config/service-enabled? component process-key)
        (let [process-data (process/spawn! "lein" process-name)]
          (log/debugf "Started %s component." process-name)
          (assoc component :process-data process-data
                           :enabled true))
        (do
          (log/debugf "Service %s not enabled; skipping component start-up."
                      process-key)
          (assoc component :enabled false)))))

  (stop [component]
    (let [process-key (:process-keyword component)
          process-name (name process-key)]
      (log/debug "Process name:" process-name)
      (log/infof "Stopping %s component ..." process-name)
      (if (config/service-enabled? component process-key)
        (let [process-data (:process-data component)]
          (log/trace "process-data:" process-data)
          (log/trace "process:" (:process process-data))
          (process/terminate! (:process-data component))
          (log/debugf "Stopped %s component." process-name)
          (assoc component :process-data nil))
        (do
          (log/debugf "Service %s not enabled; skipping component shut-down."
                      process-key)
          component)))))

(defn create-process-runner-component
  ""
  [config-builder-fn process-keyword]
  (map->ProcessRunner
    {:builder config-builder-fn
     :process-keyword process-keyword}))

(ns cmr.dev.env.manager.components.process
  (:require
    [clojure.core.async :as async]
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.process.core :as process]
    [cmr.dev.env.manager.process.util :as util]
    [com.stuartsierra.component :as component]
    [me.raynes.conch.low-level :as shell]
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

(defn stream->message
  [system topic ^java.io.InputStream input-stream]
  (let [bytes (util/make-byte-array)]
    (async/go-loop [stream input-stream
                    bytes-read (process/read-stream stream bytes)]
      (when (pos? bytes-read)
        (messaging/publish system topic (util/bytes->ascii bytes))
        (recur stream (process/read-stream stream bytes))))))

(defn spawn!
  [system & args]
  (let [process-data (apply shell/proc args)]
    (stream->message system :debug (:out process-data))
    (stream->message system :error (:err process-data))
    process-data))

(defn terminate!
  [system process-data]
  (shell/flush process-data)
  (shell/done process-data)
  (process/terminate-descendants! process-data)
  (let [exit (future (shell/exit-code process-data))]
    (shell/destroy process-data)
    @exit))

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
      (log/trace "Component keys:" (keys component))
      (log/trace "Built configuration:" cfg)
      (log/trace "Config:" (:config component))
      (if (config/service-enabled? component process-key)
        (let [process-data (spawn! component "lein" process-name)]
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
          (terminate! component (:process-data component))
          (log/debugf "Stopped %s component." process-name)
          (assoc component :process-data nil))
        (do
          (log/debugf "Service %s not enabled; skipping component shut-down."
                      process-key)
          component)))))

(defn create-component
  ""
  [config-builder-fn process-keyword]
  (map->ProcessRunner
    {:builder config-builder-fn
     :process-keyword process-keyword}))

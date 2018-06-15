(ns cmr.process.manager.components.process
  (:require
    [clojure.core.async :as async]
    [cmr.process.manager.core :as process]
    [cmr.process.manager.util :as util]
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
  process-data
  opts]
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
      (let [process-data (spawn! component "lein" process-name)]
        (log/debugf "Started %s component." process-name)
        (assoc component :process-data process-data
                         :enabled true))))

  (stop [component]
    (let [process-key (:process-keyword component)
          process-name (name process-key)]
      (log/debug "Process name:" process-name)
      (log/infof "Stopping %s component ..." process-name)
      (let [process-data (:process-data component)]
        (log/trace "process-data:" process-data)
        (log/trace "process:" (:process process-data))
        (terminate! component (:process-data component))
        (log/debugf "Stopped %s component." process-name)
        (assoc component :process-data nil)))))

(defn create-component
  ""
  [config-builder-fn process-keyword]
  (map->ProcessRunner
    {:builder config-builder-fn
     :process-keyword process-keyword}))

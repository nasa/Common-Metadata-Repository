(ns cmr.dev.env.manager.components.process
  (:require
    [clojure.core.async :as async]
    [cmr.dev.env.manager.components.dem.config :as config]
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.process.core :as process]
    [cmr.dev.env.manager.process.util :as util]
    [com.stuartsierra.component :as component]
    [me.raynes.conch.low-level :as shell]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-process
  [system service-key]
  (get-in system [service-key :process-data]))

(defn get-process-cpu
  [system service-key]
  (process/get-cpu (get-process system service-key)))

(defn get-process-descendants
  [system service-key]
  (process/get-descendants (get-process system service-key)))

(defn get-process-id
  [system service-key]
  (process/get-pid (get-process system service-key)))

(defn get-process-mem
  [system service-key]
  (process/get-mem (get-process system service-key)))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord ProcessRunner
  [builder
   process-keyword
   process-data])

(defn start
  [this]
  (let [cfg ((:builder this) (:process-keyword this))
        this (assoc this :config cfg)
        process-key (:process-keyword this)
        process-name (name process-key)]
    (log/infof "Starting %s component ..." process-name)
    (log/trace "Component keys:" (keys this))
    (log/trace "Built configuration:" cfg)
    (log/trace "Config:" (:config this))
    (if (config/service-enabled? this process-key)
      (let [process-data (spawn! this "lein" process-name)]
        (log/debugf "Started %s component." process-name)
        (assoc this :process-data process-data
                    :enabled true))
      (do
        (log/debugf "Service %s not enabled; skipping component start-up."
                    process-key)
        (assoc this :enabled false)))))

(defn stop
  [this]
  (let [process-key (:process-keyword this)
        process-name (name process-key)]
    (log/debug "Process name:" process-name)
    (log/infof "Stopping %s component ..." process-name)
    (if (config/service-enabled? this process-key)
      (let [process-data (:process-data this)]
        (log/trace "process-data:" process-data)
        (log/trace "process:" (:process process-data))
        (terminate! this (:process-data this))
        (log/debugf "Stopped %s component." process-name)
        (assoc this :process-data nil))
      (do
        (log/debugf "Service %s not enabled; skipping component shut-down."
                    process-key)
        this))))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend ProcessRunner
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  ""
  [config-builder-fn process-keyword]
  (map->ProcessRunner
    {:builder config-builder-fn
     :process-keyword process-keyword}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-status
  [this]
  ;; XXX process, http, and ping TBD
  (let [process-response nil
        http-response nil
        ping-response nil
        cpu (process/get-cpu (:process-data this))
        mem (process/get-mem (:process-data this))]
    {:process {:status :ok}
     :http {:status :ok}
     :ping {:status :ok}
     :cpu {
       :status (if (>= cpu 50) :high :ok)
       :details {:value cpu :type :percent}}
     :mem {
       :status (if (>= mem 20) :high :ok)
       :details {:value mem :type :percent}}}))

;; XXX move this into ...
;;     * Option 1: cmr.dev.env.manager.components.common
;;     * Option 2: cmr.dev.env.manager.components.checks.common
;;     see ticket https://github.com/cmr-exchange/dev-env-manager/issues/36
(defn get-summary
  [this]
  (->> this
       (get-status)
       (map (fn [[k v]] [k (:status v)]))
       (into {})))

(def healthful-behaviour
  {:get-status get-status
   :get-summary get-summary})

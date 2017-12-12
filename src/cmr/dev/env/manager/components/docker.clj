(ns cmr.dev.env.manager.components.docker
  (:require
    [cmr.dev.env.manager.components.dem.messaging :as messaging]
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.process.docker :as docker]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Docker component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DockerRunner
  [builder
   process-keyword
   opts-fn
   opts])

(defn start
  [this]
  (let [cfg ((:builder this) (:process-keyword this))
        component (assoc this :config cfg)
        opts ((:opts-fn this) this)
        process-key (:process-keyword this)
        process-name (name process-key)]
    (log/infof "Starting %s docker component ..." process-name)
    (log/trace "Component keys:" (keys this))
    (log/trace "Built configuration:" cfg)
    (log/trace "Config:" (:config this))
    (log/debug "Docker options:" opts)
    (if (config/service-enabled? this process-key)
      (do
        (docker/pull opts)
        (docker/run opts)
        (assoc this :enabled true :opts opts))
      (do
        (log/warnf (str "Docker service %s not enabled; "
                        "skipping component start-up.")
                   process-key)
        (assoc this :enabled false)))))

(defn stop
  [this]
  (let [process-key (:process-keyword this)
        process-name (name process-key)]
    (log/debug "Process name:" process-name)
    (log/infof "Stopping %s docker component ..." process-name)
    (if (config/service-enabled? this process-key)
      (do
        (docker/stop (:opts this))
        this)
      (do
        (log/warnf (str "Docker service %s not enabled; "
                        "skipping component shut-down.")
                   process-key)
        this))))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend DockerRunner
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  ""
  [config-builder-fn process-keyword docker-opts-fn]
  (map->DockerRunner
    {:builder config-builder-fn
     :process-keyword process-keyword
     :opts-fn docker-opts-fn}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-status
  [this]
  (let [response (docker/state (:opts this))]
    {:docker {
       :status (keyword (:Status response))
       :details response}}))

(def healthful-behaviour
  {:get-status get-status})

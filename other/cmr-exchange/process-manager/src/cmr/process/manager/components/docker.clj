(ns cmr.process.manager.components.docker
  (:require
    [cmr.process.manager.docker :as docker]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Docker Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

(defn get-container-pid
  [system service-key]
  (docker/pid (get-opts system service-key)))

(defn get-container-cpu
  [system service-key]
  (docker/get-cpu (get-opts system service-key)))

(defn get-container-mem
  [system service-key]
  (docker/get-mem (get-opts system service-key)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DockerRunner
  [process-keyword
   opts])

(defn start
  [this]
  (let [opts (:opts this)
        process-key (:process-keyword this)
        process-name (name process-key)]
    (log/infof "Starting %s docker component ..." process-name)
    (log/trace "Component keys:" (keys this))
    (log/trace "Config:" (:config this))
    (log/debug "Docker options:" opts)
    (docker/pull opts)
    (docker/run opts)
    (assoc this :enabled true :opts opts)))

(defn stop
  [this]
  (let [process-key (:process-keyword this)
        process-name (name process-key)]
    (log/debug "Process name:" process-name)
    (log/infof "Stopping %s docker component ..." process-name)
    (docker/stop (:opts this))
    (assoc this :enabled nil :opts nil)))


(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend DockerRunner
        component/Lifecycle
        lifecycle-behaviour)

(defn create-component
  ""
  [process-keyword opts]
  (map->DockerRunner
    {:process-keyword process-keyword
     :opts opts}))

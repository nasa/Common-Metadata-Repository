(ns cmr.exchange.common.components.logging
  (:require
    [clojusc.twig :as logger]
    [cmr.exchange.common.components.config :as config]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Logging [])

(defn start
  [this]
  (log/info "Starting logging component ...")
  (let [log-level (keyword (config/log-level this))
        log-nss (config/log-nss this)]
    (log/debug "Setting up logging with level" log-level)
    (log/trace "Log config:" (get-in this [:config :data :logging]))
    (log/debug "Logging namespaces:" log-nss)
    (if (true? (config/log-color? this))
      (do
        (log/debug "Enabling color logging ...")
        (logger/set-level! log-nss log-level))
      (logger/set-level! log-nss log-level logger/no-color-log-formatter))
    (log/debug "Started logging component.")
    this))

(defn stop
  [this]
  (log/info "Stopping logging component ...")
  (log/debug "Stopped logging component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Logging
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Logging {}))

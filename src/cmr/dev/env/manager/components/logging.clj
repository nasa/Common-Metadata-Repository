(ns cmr.dev.env.manager.components.logging
  (:require
    [clojusc.twig :as logger]
    [cmr.dev.env.manager.components.dem.config :as config]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Logging [])

(defn start
  [this]
  (log/info "Starting logging component ...")
  (let [log-level (config/log-level this)
        log-nss (vec (config/log-nss this))]
    (log/debug "Setting up logging with level" log-level)
    (log/debug "Logging namespaces:" log-nss)
    (logger/set-level! log-nss log-level)
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

(defn create-component
  ""
  []
  (->Logging))

(ns cmr.dev.env.manager.components.dem.config
  (:require
    [cmr.dev.env.manager.config :as config]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn active-config
  ""
  [system & args]
  (let [base-keys [:config config/config-key]]
    (if-not (seq args)
      (get-in system base-keys)
      (get-in system (concat base-keys args)))))

(defn app-dir
  [system]
  (active-config system :app-dir))

(defn logging
  [system]
  (active-config system :logging))

(defn log-level
  [system]
  (active-config system :logging :level))

(defn log-nss
  [system]
  (active-config system :logging :nss))

(defn enabled-services
  [system]
  (active-config system :enabled-services))

(defn service-enabled?
  [system service-key]
  (contains? (enabled-services system) service-key))

(defn messaging-type
  [system]
  (active-config system :messaging :type))

(defn elastic-search-opts
  [system]
  (active-config system :elastic-search))

(defn elastic-search-head-opts
  [system]
  (active-config system :elastic-search-head))

(defn timer-delay
  [system]
  (active-config system :timer :delay))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config
  [builder])

(defn start
  [this]
  (log/info "Starting config component ...")
  (log/debug "Started config component.")
  (let [cfg ((:builder this))]
    (log/trace "Built configuration:" cfg)
    (merge this cfg)))

(defn stop
  [this]
  (log/info "Stopping config component ...")
  (log/debug "Stopped config component.")
  (assoc this :dem nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Config
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  ""
  [config-builder-fn]
  (map->Config
    {:builder config-builder-fn}))

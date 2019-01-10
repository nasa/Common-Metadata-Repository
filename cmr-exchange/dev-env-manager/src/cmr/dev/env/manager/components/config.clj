(ns cmr.dev.env.manager.components.config
  (:require
    [cmr.dev.env.manager.config :as config]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn active-config
  ""
  [system cfg-level & args]
  (let [config-component-key :config
        base-keys [config-component-key cfg-level]]
    (if-not (seq args)
      (get-in system base-keys)
      (get-in system (concat base-keys args)))))

;; DEM-level config

(defn elastic-search-opts
  [system]
  (active-config system config/internal-cfg-key :elastic-search))

(defn elastic-search-head-opts
  [system]
  (active-config system config/internal-cfg-key :elastic-search-head))

(defn enabled-services
  [system]
  (active-config system config/internal-cfg-key :enabled-services))

(defn log-level
  [system]
  (active-config system config/internal-cfg-key :logging :level))

(defn log-nss
  [system]
  (active-config system config/internal-cfg-key :logging :nss))

(defn logging
  [system]
  (active-config system config/internal-cfg-key :logging))

(defn messaging-type
  [system]
  (active-config system config/internal-cfg-key :messaging :type))

(defn service-enabled?
  [system service-key]
  (contains? (enabled-services system) service-key))

(defn timer-delay
  [system]
  (active-config system config/internal-cfg-key :timer :delay))

;; Service-level config

;; XXX TBD

;; CMR-level config

(defn service-paths
  [system service-key]
  (active-config system config/external-cfg-key service-key :source-paths))

(defn service-ports
  [system]
  (active-config system config/external-cfg-key :ports))

(defn service-port
  [system service-key]
  (active-config system config/external-cfg-key :ports service-key))

;; Mixed-level config

(defn enabled-service-paths
  [system]
  (->> (enabled-services system)
       (map (fn [x] [x (service-paths system x)]))
       (filter second)
       (into {})))

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

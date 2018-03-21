(ns cmr.graph.components.config
  (:require
   [cmr.graph.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-cfg
  [system]
  (get-in system [:config :data]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn elastic-host
  [system]
  (get-in (get-cfg system) [:elastic :host]))

(defn elastic-port
  [system]
  (get-in (get-cfg system) [:elastic :port]))

(defn elastic-timeout
  [system]
  (get-in (get-cfg system) [:elastic :timeout]))

(defn http-port
  [system]
  (get-in (get-cfg system) [:httpd :port]))

(defn http-docroot
  [system]
  (get-in (get-cfg system) [:httpd :docroot]))

(defn log-level
  [system]
  (get-in (get-cfg system) [:logging :level]))

(defn log-nss
  [system]
  (get-in (get-cfg system) [:logging :nss]))

(defn neo4j-host
  [system]
  (get-in (get-cfg system) [:neo4j :host]))

(defn neo4j-port
  [system]
  (get-in (get-cfg system) [:neo4j :port]))

(defn neo4j-db-path
  [system]
  (get-in (get-cfg system) [:neo4j :db-path]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config [data])

(defn start
  [this]
  (log/info "Starting config component ...")
  (log/debug "Started config component.")
  (let [cfg (config/data)]
    (log/trace "Built configuration:" cfg)
    (assoc this :data cfg)))

(defn stop
  [this]
  (log/info "Stopping config component ...")
  (log/debug "Stopped config component.")
  (assoc this :data nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Config
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Config {}))

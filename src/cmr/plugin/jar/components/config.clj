(ns cmr.plugin.jar.components.config
  (:require
   [cmr.plugin.jar.config :as config]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log])
  (:import
   (clojure.lang Keyword)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-cfg
  [system]
  (->> [:config :data]
       (get-in system)
       (into {})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Config Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn log-color?
  [system]
  (or (get-in (get-cfg system) [:cmr :opendap :logging :color])
      (get-in (get-cfg system) [:logging :color])))

(defn log-level
  [system]
  (get-in (get-cfg system) [:logging :level]))

(defn log-nss
  [system]
  (get-in (get-cfg system) [:logging :nss]))

(defn default-plugin-name
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :plugin-name]))

(defn default-plugin-type
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :plugin-type]))

(defn default-config-file
  [system]
  (get-in (get-cfg system) [:plugin :registry :default :config-file]))

(defn default-route-keys
  [system]
  (get-in (get-cfg system) [:plugin :registry :web :route-keys]))

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
  this)

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

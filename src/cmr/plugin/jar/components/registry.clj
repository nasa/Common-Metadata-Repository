(ns cmr.plugin.jar.components.registry
  (:require
    [clojusc.twig :as logger]
    [com.stuartsierra.component :as component]
    [cmr.plugin.jar.components.config :as config]
    [cmr.plugin.jar.core :as plugin]
    [cmr.plugin.jar.types.web.core :as web]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Registry Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn jars
  [system]
  (get-in system [:plugin :registry :jars]))

(defn routes
  [system]
  (get-in system [:plugin :registry :routes]))

(defn assembled-routes
  [system]
  (get-in system [:plugin :registry :assembled-routes]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord PluginRegistry [])

(defn start
  [this]
  (log/info "Starting plugin registry component ...")
  (let [plugin-name (config/default-plugin-name this)
        plugin-type (config/default-plugin-type this)
        in-jar-filepath (config/default-config-file this)
        route-keys (config/default-route-keys this)
        api-key (config/api-route-key this)
        site-key (config/site-route-key this)
        reducer (config/jarfiles-reducer this)
        jarfiles (plugin/jarfiles plugin-name plugin-type reducer)]
    (log/debug "Started plugin registry component.")
    (-> this
        (assoc-in [:registry :jars]
                  (plugin/named-jars jarfiles))
        (assoc-in [:registry :routes]
                  (web/plugins-routes jarfiles
                                      in-jar-filepath
                                      route-keys
                                      api-key
                                      site-key))
        (assoc-in [:registry :assembled-routes]
                  (web/assemble-routes jarfiles
                                       plugin-name
                                       plugin-type
                                       in-jar-filepath
                                       route-keys
                                       api-key
                                       site-key)))))

(defn stop
  [this]
  (log/info "Stopping plugin registry component ...")
  (log/debug "Stopped plugin registry component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend PluginRegistry
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->PluginRegistry {}))

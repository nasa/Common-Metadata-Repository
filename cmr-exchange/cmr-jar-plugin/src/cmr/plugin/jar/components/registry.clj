(ns cmr.plugin.jar.components.registry
  "This component defines the plugin registry for the CMR JAR/MANIFEST plugin
  library."
  (:require
    [clojusc.twig :as logger]
    [com.stuartsierra.component :as component]
    [cmr.plugin.jar.components.config :as config]
    [cmr.plugin.jar.core :as plugin]
    [cmr.plugin.jar.types.web.routes :as routes]
    [taoensso.timbre :as log]))

(defn trace-pass-thru
  [msg data]
  (log/tracef "%s: %s" msg data)
  data)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Registry Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn jars
  [system]
  (get-in system [:plugin :registry :jars]))

(defn routes
  [system]
  (get-in system [:plugin :registry :routes]))

(defn resolved-routes
  [system]
  (let [{apis-fns :api sites-fns :site}
        (get-in system [:plugin :registry :resolved-routes-fns])]
    (log/trace "sites-fn:" apis-fns)
    (log/trace "apis-fn:" sites-fns)
    {:api apis-fns
     :site sites-fns}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord PluginRegistry [registry])

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
    (log/debug "plugin-name:" plugin-name)
    (log/debug "plugin-type:" plugin-type)
    (log/debug "in-jar-filepath:" in-jar-filepath)
    (log/debug "route-keys:" route-keys)
    (log/debug "api-key:" api-key)
    (log/debug "site-key:" site-key)
    (log/debug "reducer:" reducer)
    (log/debug "jarfiles:" jarfiles)
    (log/debug "Started plugin registry component.")
    (-> this
        (assoc-in [:registry :jars]
                  (trace-pass-thru "tagged-jars"
                  (plugin/tagged-jars
                   jarfiles
                   plugin-name
                   plugin-type)))
        (assoc-in [:registry :routes]
                  (trace-pass-thru "plugins-routes"
                  (routes/plugins-routes
                   jarfiles
                   in-jar-filepath
                   route-keys
                   api-key
                   site-key)))
        (assoc-in [:registry :resolved-routes-fns]
                  (trace-pass-thru "assemble-routes-fns"
                  (routes/resolved-routes-fns
                   jarfiles
                   plugin-name
                   plugin-type
                   in-jar-filepath
                   route-keys
                   api-key
                   site-key))))))

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

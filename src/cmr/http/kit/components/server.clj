(ns cmr.http.kit.components.server
  (:require
    [com.stuartsierra.component :as component]
    [cmr.exchange.common.util :as util]
    [cmr.http.kit.components.config :as config]
    [org.httpkit.server :as server]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord HTTPD [port])

(defn start
  "The `entry-point` in configuration needs to be of the form
  `name.space/function-name` and should point to a function that takes at least
  one parameter: the httpd component/system data structure.

  Furthermore, when that `entry-point` function is called, it requires that
  two other configuration values have been set:
  * API routes
  * web site routes

  Note that the API route takes an additional parameter: the version of the
  API; unlike the site routes, the API routes are built at the time of the
  HTTP request (in order to support versioned APIs).

  For more information on the expected placement in configuration, see
  `cmr.http.kit.components.config`."
  [this]
  (log/info "Starting httpd component ...")
  (let [port (or (:port this)
                 (config/http-port this))
        entry-point (config/http-entry-point-fn this)
        server (server/run-server (entry-point this)
                                  {:port port})]
    (log/trace "Entry point:" entry-point)
    (log/trace "System data:" (into {} this))
    (log/debugf "HTTPD is listening on port %s" port)
    (log/debug "Started httpd component.")
    (assoc this :server server)))

(defn stop
  [this]
  (log/info "Stopping httpd component ...")
  (if-let [server (:server this)]
    (server))
  (assoc this :server nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend HTTPD
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  ([]
    (map->HTTPD {}))
  ([port]
    (map->HTTPD {:port port})))

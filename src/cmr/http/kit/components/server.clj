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

(defrecord HTTPD [])

(defn start
  [this]
  (log/info "Starting httpd component ...")
  (let [port (config/http-port this)
        entry-point (util/resolve-fully-qualified-fn
                     (config/http-entry-point-fn this))
        server (server/run-server (entry-point this)
                                  {:port port})]
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
  []
  (map->HTTPD {}))

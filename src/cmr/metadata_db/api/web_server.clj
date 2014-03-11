(ns cmr.metadata-db.api.web-server
  (:require [cmr.metadata-db.api.routes :as routes]
            [cmr.common.lifecycle :as lifecycle]
            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :refer (debug info warn error)])
  (:import org.eclipse.jetty.server.Server))

(defrecord WebServer
  [
   ;; The port Jetty will be running on
   port
   ;;The actual Jetty instance
   server
  ]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [route (routes/make-api system)
          port (:port this)
          server (jetty/run-jetty route {:port port :join? false})]
      (info "Jetty started on port" port)
      (assoc this :server server)))

  (stop
    [this system]
    (if-let [^Server server (:server this)]
      (.stop server))
    (assoc this :server nil)))

(defn create-web-server
    "Creates web server."
    [{:keys [port]}]
    (map->WebServer {:port port}))



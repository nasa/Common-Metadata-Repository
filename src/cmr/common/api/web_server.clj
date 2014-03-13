(ns cmr.common.api.web-server
  "Defines a web server component."
  (:require [cmr.common.lifecycle :as lifecycle]
            [ring.adapter.jetty :as jetty]
            [cmr.common.log :refer (debug info warn error)])
  (:import org.eclipse.jetty.server.Server))

(defrecord WebServer
  [
   ;; The port Jetty will be running on
   port

   ;; A function that will return the routes. Should accept a single argument of the system.
   routes-fn

   ;;The actual Jetty instance
   server
  ]

  lifecycle/Lifecycle

  (start
    [this system]
    (let [{:keys [port routes-fn]} this
          routes (routes-fn system)
          server (jetty/run-jetty routes {:port port :join? false})]
      (info "Jetty started on port" port)
      (assoc this :server server)))

  (stop
    [this system]
    (if-let [^Server server (:server this)]
      (.stop server))
    (assoc this :server nil)))


(defn create-web-server
  "Creates a new web server. Accepts argument of port and a routes function that should accept
  system argument and return compojure routes to use."
  [port routes-fn]
  (map->WebServer {:port port
                   :routes-fn routes-fn}))
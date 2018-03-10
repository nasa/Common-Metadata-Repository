(ns cmr.graph.rest.app
  (:require
   [cmr.graph.health :as health]
   [cmr.graph.rest.response :as response]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn health-handler
  [component]
  (fn [request]
    (-> component
        health/components-ok?
        (response/json request))))

(def ping-handler
  (partial response/json {:result :pong}))

(defn app
  [httpd-component]
  (ring/ring-handler
    (ring/router
      [["/health" {:get (health-handler httpd-component)}]
       ["/ping" {:get ping-handler
                 :port ping-handler}]])
    (constantly response/not-found)))

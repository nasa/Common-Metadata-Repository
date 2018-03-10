(ns cmr.graph.rest.handler
  (:require
   [cmr.graph.health :as health]
   [cmr.graph.rest.response :as response]
   [taoensso.timbre :as log]))

(defn health
  [component]
  (fn [request]
    (-> component
        health/components-ok?
        (response/json request))))

(def ping
  (partial response/json {:result :pong}))

(ns cmr.graph.rest.app
  (:require
   [cmr.graph.rest.route :as route]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn app
  [httpd-component]
  (ring/ring-handler
    (ring/router
      [(route/health httpd-component)
       route/ping])
    route/fallback))

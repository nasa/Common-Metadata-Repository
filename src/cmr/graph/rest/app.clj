(ns cmr.graph.rest.app
  (:require
   [cmr.graph.rest.handler :as handler]
   [cmr.graph.rest.route :as route]
   [ring.middleware.defaults :as ring-defaults]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn rest-api-routes
  [httpd-component]
  (concat
   (route/movie-demo httpd-component)
   (route/health httpd-component)
   route/ping))

(defn app
  [httpd-component]
  (-> httpd-component
      rest-api-routes
      ring/router
      (ring/ring-handler handler/fallback)
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)))

(ns cmr.graph.rest.app
  (:require
   [clojure.java.io :as io]
   [cmr.graph.rest.handler :as handler]
   [cmr.graph.rest.middleware :as middleware]
   [cmr.graph.rest.route :as route]
   [ring.middleware.defaults :as ring-defaults]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn rest-api-routes
  [httpd-component]
  (concat
   (route/collections httpd-component)
   (route/relationships httpd-component)
   (route/static httpd-component)
   (route/movie-demo httpd-component)
   (route/admin httpd-component)
   (route/dangerous httpd-component)))

(defn app
  [httpd-component]
  (-> httpd-component
      rest-api-routes
      ring/router
      (ring/ring-handler handler/fallback)
      (ring-defaults/wrap-defaults ring-defaults/api-defaults)
      (middleware/wrap-cors)))

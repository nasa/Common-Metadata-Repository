(ns cmr.opendap.app.core
  (:require
   [clojure.java.io :as io]
   [cmr.opendap.app.handler.core :as handler]
   [cmr.opendap.app.middleware :as middleware]
   [cmr.opendap.app.route :as route]
   [cmr.opendap.components.config :as config]
   [ring.middleware.defaults :as ring-defaults]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn rest-api-routes
  [httpd-component]
  (concat
   (route/ous-api httpd-component)
   (route/admin-api httpd-component)))

(defn site-routes
  [httpd-component]
  (concat
   (route/main httpd-component)
   (route/docs httpd-component)
   (route/redirects httpd-component)
   (route/static httpd-component)))

(defn all-routes
  [httpd-component]
  (concat
    (rest-api-routes httpd-component)
    (site-routes httpd-component)
    route/testing))

(defn main
  [httpd-component]
  (let [docs-resource (config/http-docs httpd-component)
        assets-resource (config/http-assets httpd-component)]
    (-> httpd-component
        all-routes
        (ring/router (middleware/reitit-auth httpd-component))
        ring/ring-handler
        (ring-defaults/wrap-defaults ring-defaults/api-defaults)
        (middleware/wrap-resource httpd-component)
        middleware/wrap-trailing-slash
        middleware/wrap-cors
        (middleware/wrap-not-found httpd-component))))

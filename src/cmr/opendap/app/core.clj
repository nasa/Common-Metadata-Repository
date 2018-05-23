(ns cmr.opendap.app.core
  (:require
   [clojure.java.io :as io]
   [cmr.opendap.app.handler.core :as handler]
   [cmr.opendap.app.middleware :as middleware]
   [cmr.opendap.app.routes.site :as site-routes]
   [cmr.opendap.app.routes.rest :as rest-routes]
   [cmr.opendap.components.config :as config]
   [ring.middleware.defaults :as ring-defaults]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn main
  [httpd-component]
  (let [docs-resource (config/http-docs httpd-component)
        assets-resource (config/http-assets httpd-component)]
    (-> httpd-component
        site-routes/all
        (middleware/wrap-api-version-dispatch
          httpd-component
          (middleware/reitit-auth httpd-component))
        (ring-defaults/wrap-defaults ring-defaults/api-defaults)
        (middleware/wrap-resource httpd-component)
        middleware/wrap-trailing-slash
        middleware/wrap-cors
        (middleware/wrap-not-found httpd-component))))

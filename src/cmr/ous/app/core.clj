(ns cmr.opendap.app.core
  (:require
   [cmr.http.kit.app.core :as base-app]
   [cmr.http.kit.app.middleware :as base-middleware]
   [cmr.opendap.app.middleware :as middleware]
   [taoensso.timbre :as log]))

(defn main
  [httpd-component]
  (log/trace "httpd-component keys:" (keys httpd-component))
  (let [{site-routes :site-routes :as route-data}
        (base-app/collected-routes httpd-component)]
    (-> site-routes
        ;; initial routes and middleware are reitit-based
        (middleware/wrap-api-version-dispatch route-data httpd-component)
        ;; the wrap-api-version-dispatch makes a call which converts the
        ;; reitit-based middleware/routes to ring; from here on out, all
        ;; middleware is ring-based
        (base-middleware/wrap-ring-middleware httpd-component))))

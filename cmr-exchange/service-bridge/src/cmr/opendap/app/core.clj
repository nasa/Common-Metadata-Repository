(ns cmr.opendap.app.core
  (:require
   [cmr.http.kit.app.core :as base-app]
   [cmr.http.kit.app.middleware :as base-middleware]
   [cmr.opendap.app.middleware :as middleware]
   [taoensso.timbre :as log]))

(defn main
  "CMR Service Bridge is a framework that supports the addition of new features
  via plugins. As a framework, it offers a limited number of routes and handlers,
  but it is the individual plugins that provide the functionality.

  As you can see below, site-routes are extracted from the route-data; the reason
  we do not extract api-routes (i.e. REST resources), is because api-routes are
  versioned. As such, a different version of the route may be required at
  request time. To satisfy that condition, we build the routes dynamically
  inside the middleware that handles the API versioning.

  Lastly, a whole series of middleware (both community-provided and CMR-created)
  are applied after we perform versioning dispatch. These may be viewed in the
  cmr-http-kit project."
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

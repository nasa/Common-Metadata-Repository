(ns cmr.opendap.app.middleware
  "Custom ring middleware for CMR OPeNDAP."
  (:require
   [clojusc.twig :refer [pprint]]
   [cmr.ous.util.http.request :as ous-request]
   [cmr.http.kit.app.middleware :as middleware]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.components.auth :as auth]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn wrap-auth
  "Ring-based middleware for supporting the protection of routes using the CMR
  Access Control service and CMR Legacy ECHO support.

  In particular, this wrapper allows for the protection of routes by both roles
  as well as concept-specific permissions. This is done by annotating the routes
  per the means described in the reitit library's documentation."
  [handler system]
  (fn [req]
    (log/debug "Running perms middleware ...")
    (auth/check-route-access system handler req)))

(defn reitit-auth
  [system]
  "This auth middleware is specific to reitit, providing the data structure
  necessary that will allow for the extraction of roles and permissions
  settings from the request.

  For more details, see the docstring above for `wrap-auth`."
  {:data
    {:middleware [#(-> %
                       middleware/wrap-request-id
                       (wrap-auth system))]}})

(defn wrap-api-version-dispatch
  ""
  ([site-routes route-data system]
    (wrap-api-version-dispatch
      site-routes route-data system (reitit-auth system)))
  ([site-routes {:keys [main-api-routes-fn plugins-api-routes-fns]} system opts]
    (fn [req]
      (let [api-version (ous-request/accept-api-version system req)
            plugins-api-routes (vec (mapcat #(% system api-version) plugins-api-routes-fns))
            api-routes (vec (main-api-routes-fn system api-version))
            routes (concat (vec site-routes) plugins-api-routes api-routes)
            handler (ring/ring-handler (ring/router routes opts))]
        (log/debug "API version:" api-version)
        (log/debug "Made routes:" (pprint routes))
        (response/version-media-type
          (handler req)
          (ous-request/accept-media-type-format system req))))))

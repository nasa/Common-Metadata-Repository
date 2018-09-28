(ns cmr.opendap.app.middleware
  "Custom ring middleware for CMR OPeNDAP."
  (:require
   [cmr.http.kit.response :as response]
   [cmr.opendap.components.auth :as auth]
   [cmr.opendap.http.request :as request]
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
    {:middleware [#(wrap-auth % system)]}})

(defn wrap-api-version-dispatch
  ""
  ([site-routes route-data system]
    (wrap-api-version-dispatch
      site-routes route-data system (reitit-auth system)))
  ([site-routes {:keys [main-api-routes-fn plugins-api-routes]} system opts]
    (fn [req]
      (log/debug "Got site-routes:" (vec site-routes))
      (let [api-version (request/accept-api-version system req)
            api-routes (main-api-routes-fn system api-version)
            _ (log/trace "Got plugins-api-routes:" (vec plugins-api-routes))
            _ (log/trace "Got api-routes:" (vec api-routes))
            routes (concat site-routes plugins-api-routes api-routes)
            _ (log/trace "Got assembled routes:" (vec routes))
            handler (ring/ring-handler (ring/router routes opts))]
        (log/debug "API version:" api-version)
        (log/debug "Made routes:" (vec routes))
        (response/version-media-type
          (handler req)
          (request/accept-media-type-format system req))))))

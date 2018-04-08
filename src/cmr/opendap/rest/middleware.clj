(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [cmr.opendap.auth.core :as auth]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

(defn wrap-cors
  "Ring-based middleware for supporting CORS requests."
  [handler]
  (fn [request]
    (response/cors request (handler request))))

(defn wrap-auth
  "Ring-based middleware for supporting the protection of routes using the CMR
  Access Control service and CMR Legacy ECHO support.

  In particular, this wrapper allows for the protection of routes by both roles
  as well as concept-specific permissions. This is done by annotating the routes
  per the means described in the reitit library's documentation."
  [handler system]
  (fn [request]
    (log/debug "Running perms middleware ...")
    (auth/check-route-access system handler request)))

(defn reitit-auth
  [system]
  "This auth middleware is specific to reitit, providing the data structure
  necessary that will allow for the extraction of roles and permissions
  settings from the request.

  For more details, see the docstring above for `wrap-auth`."
  {:data
    {:middleware [#(wrap-auth % system)]}})

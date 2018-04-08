(ns cmr.opendap.rest.middleware
  "Custom ring middleware for CMR Graph."
  (:require
   [cmr.opendap.auth.permissions :as permissions]
   [cmr.opendap.auth.roles :as roles]
   [cmr.opendap.auth.token :as token]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.http.response :as response]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn wrap-cors
  "Ring-based middleware for supporting CORS requests."
  [handler]
  (fn [request]
    (response/cors request (handler request))))

(defn wrap-acls
  "Ring-based middleware for supporting the protection of routes using the CMR
  Access Control service and CMR Legacy ECHO support.

  In particular, this wrapper allows for the protection of routes by both roles
  as well as concept-specific permissions. This is done by annotating the routes
  per the means described in the reitit library's documentation."
  [handler system]
  (fn [request]
    (log/debug "Running perms middleware ...")
    ;; XXX Before performing any GETs/POSTs against CMR Access Control or ECHO,
    ;;     make sure that's actually necessary, only doing it in the event that
    ;;     the route is annotated for permissions
    (let [route-roles (roles/route-annotation request)
          cmr-base-url (config/cmr-base-url system)
          user-token (token/extract request)
          user-id (token/->cached-user system cmr-base-url user-token)]
      (log/trace "cmr-base-url:" cmr-base-url)
      (log/trace "user-token:" user-token)
      (log/trace "user-id:" user-id)
      (log/debug "route-roles:" route-roles)
      ;; XXX For now, there is only the admin role in the CMR, so we'll just
      ;;     keep this specific to that, for now. Later, if more roles are
      ;;     used, we'll want to make this more generic ...
      (cond route-roles
            (if (roles/admin? system route-roles cmr-base-url user-token user-id)
              (handler request)
              (response/not-allowed
                "You do not have permissions to access that resource."))
            ;; XXX check for permissions-based route annotations
            :else
            (handler request)))))

(defn reitit-acls
  [system]
  "This is an example of non-Ring-middleware, specific to reitit. For more
  details, see the documentation for `wrap-acls`."
  {:data
    {:middleware [#(wrap-acls % system)]}})

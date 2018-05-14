(ns cmr.opendap.auth.core
  "This namespace represents the authorization API for CMR OPeNDAP. This is
  where the rest of the application goes when it needs to perform checks on
  roles or permissions for a given user and/or concept.

  Currently, this namespace is only used by the REST middleware that checks
  resources for authorization."
  (:require
   [cmr.opendap.auth.permissions :as permissions]
   [cmr.opendap.auth.roles :as roles]
   [cmr.opendap.auth.token :as token]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [cmr.opendap.errors :as errors]
   [cmr.opendap.http.response :as response]
   [taoensso.timbre :as log]))

(defn check-roles
  "A supporting function for `check-roles-permissions` that handles the roles
  side of things."
  [system handler request route-roles user-token user-id]
  (log/debug "Checking roles annotated in routes ...")
  (let [lookup (roles/cached-admin system user-token user-id)
        errors (:errors lookup)]
    (if errors
      (response/not-allowed errors/no-permissions errors)
      (if (roles/admin? route-roles lookup)
        (handler request)
        (response/not-allowed errors/no-permissions)))))

(defn check-permissions
  "A supporting function for `check-roles-permissions` that handles the
  permissions side of things."
  [system handler request route-permissions user-token user-id]
  (let [concept-id (permissions/route-concept-id request)
        lookup (permissions/cached-concept
                system user-token user-id concept-id)
        errors (:errors lookup)]
    (log/debug "Checking permissions annotated in routes ...")
    (if errors
      (response/not-allowed errors/no-permissions errors)
      (if (permissions/concept? route-permissions
                                lookup
                                concept-id)
        (handler request)
        (response/not-allowed errors/no-permissions)))))

(defn check-roles-permissions
  "A supporting function for `check-route-access` that handles the actual
  checking."
  [system handler request route-roles route-permissions]
  (if-let [user-token (token/extract request)]
    (let [user-lookup (token/->cached-user system user-token)
          errors (:errors user-lookup)]
      (log/debug "ECHO token provided; proceeding ...")
      (log/trace "user-lookup:" user-lookup)
      (if errors
        (response/not-allowed errors/token-required errors)
        (do
          (log/trace "user-token: [REDACTED]")
          (log/trace "user-id:" user-lookup)
          (cond ;; XXX For now, there is only the admin role in the CMR, so
                ;;     we'll just keep this specific to that for now. Later, if
                ;;     more roles are used, we'll want to make this more
                ;;     generic ...
                route-roles
                (check-roles
                 system handler request route-roles user-token user-lookup)

                route-permissions
                (check-permissions system
                                   handler
                                   request
                                   route-permissions
                                   user-token
                                   user-lookup)))))
    (do
      (log/warn "ECHO token not provided for protected resource")
      (response/not-allowed errors/token-required))))

(defn check-route-access
  "This is the primary function for this namespace, utilized directly by CMR
  OPeNDAP's authorization middleware. Given a request which contains
  route-specific authorization requirements and potentially a user token,
  it checks against these as well as the level of access require for any
  requested concepts."
  [system handler request]
  ;; Before performing any GETs/POSTs against CMR Access Control or ECHO,
  ;; let's make sure that's actually necessary, only doing it in the cases
  ;; where the route is annotated for roles/permissions.
  (let [route-roles (roles/route-annotation request)
        route-permissions (permissions/route-annotation request)]
    (if (or route-roles route-permissions)
      (do
        (log/debug (str "Either roles or permissions were annotated in "
                        "routes; checking ACLs ..."))
        (log/debug "route-roles:" route-roles)
        (log/debug "route-permissions:" route-permissions)
        (check-roles-permissions
         system handler request route-roles route-permissions))
      (do
        (log/debug (str "Neither roles nor permissions were annotated in "
                        "the routes; skipping ACL check ..."))
        (handler request)))))

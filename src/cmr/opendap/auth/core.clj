(ns cmr.opendap.auth.core
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
  [system handler request route-roles cmr-base-url user-token user-id]
  (log/debug "Roles annotated in routes ...")
  (if (roles/admin? system
                    route-roles
                    cmr-base-url
                    user-token
                    user-id)
    (handler request)
    (response/not-allowed errors/no-permissions)))

(defn check-permissions
  [system handler request route-permissions cmr-base-url user-token user-id]
  (let [concept-id (permissions/route-concept-id request)]
    (log/debug "Permissions annotated in routes ...")
    (if (permissions/concept? system
                              route-permissions
                              cmr-base-url
                              user-token
                              user-id
                              concept-id)
      (handler request)
      (response/not-allowed errors/no-permissions))))

(defn check-roles-permissions
  [system handler request route-roles route-permissions]
  (if-let [user-token (token/extract request)]
    (do
      (log/debug "ECHO token provided; proceeding ...")
      (let [cmr-base-url (config/cmr-base-url system)
            user-id (token/->cached-user system cmr-base-url user-token)]
        (log/trace "cmr-base-url:" cmr-base-url)
        (log/trace "user-token:" user-token)
        (log/trace "user-id:" user-id)
        (cond ;; XXX For now, there is only the admin role in the CMR, so
              ;;     we'll just keep this specific to that for now. Later, if
              ;;     more roles are used, we'll want to make this more
              ;;     generic ...
              route-roles
              (check-roles
               system handler request route-roles cmr-base-url user-token
               user-id)

              route-permissions
              (check-permissions system handler request route-permissions
               cmr-base-url user-token user-id))))
    (do
      (log/warn "ECHO token not provided for protected resource")
      (response/not-allowed errors/token-required))))

(defn check-route-access
  [system handler request]
  ;; Before performing any GETs/POSTs against CMR Access Control or ECHO,
  ;; let's make sure that's actually necessary, only doing it in the event
  ;; that the route is annotated for roles/permissions.
  (let [route-roles (roles/route-annotation request)
        route-permissions (permissions/route-annotation request)]
    (if (or route-roles route-permissions)
      (do
        (log/debug (str "Either roles or permissions were annotated in "
                        "routes; checking ACLs ..."))
        (log/debug "route-roles:" route-roles)
        (log/debug "route-permissions:" route-permissions)
        (check-roles-permissions system handler request route-roles route-permissions))
      (do
        (log/debug (str "Neither roles nor permissions were annotated in "
                        "the routes; skipping ACL check ..."))
        (handler request)))))

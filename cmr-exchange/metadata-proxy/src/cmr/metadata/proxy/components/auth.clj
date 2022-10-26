(ns cmr.metadata.proxy.components.auth
  "This namespace represents the authorization API for CMR OPeNDAP. This is
  where the rest of the application goes when it needs to perform checks on
  roles or permissions for a given user and/or concept.

  Currently, this namespace is only used by the REST middleware that checks
  resources for authorization."
  (:require
   [clojure.set :as set]
   [cmr.authz.components.caching :as caching]
   [cmr.authz.components.config :as config]
   [cmr.authz.errors :as errors]
   [cmr.authz.permissions :as permissions]
   [cmr.authz.roles :as roles]
   [cmr.authz.token :as token]
   [cmr.exchange.common.results.errors :as base-errors]
   [cmr.http.kit.response :as response]
   [com.stuartsierra.component :as component]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/utility Data & Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn admin-role?
  "Check to see if the roles of a given token+user match the required roles for
  the route."
  [route-roles cache-lookup]
  (log/debug "Roles required-set:" route-roles)
  (log/debug "Roles has-set:" cache-lookup)
  (seq (set/intersection cache-lookup route-roles)))

(defn concept-permission?
  "Check to see if the concept permissions of a given token+user match the
  required permissions for the route."
  [route-perms cache-lookup concept-id]
  (let [id (keyword concept-id)
        required (permissions/cmr-acl->reitit-acl route-perms)
        required-set (id required)
        has-set (id cache-lookup)]
    (log/debug "cache-lookup:" cache-lookup)
    (log/debug "Permissions required-set:" required-set)
    (log/debug "Permissions has-set:" has-set)
    (seq (set/intersection required-set has-set))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Caching Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn cached-user
  "Look up the user for a token in the cache; if there is a miss, make the
  actual call for the lookup."
  [system token]
  (try
    (caching/lookup
     system
     (token/user-id-key token)
     #(token/->user (config/get-echo-rest-url system) token))
    (catch Exception e
      (log/error e)
      {:errors (base-errors/exception-data e)})))

(defn cached-admin-role
  "Look up the roles for token+user in the cache; if there is a miss, make the
  actual call for the lookup."
  [system token user-id]
  (try
    (caching/lookup system
                    (roles/roles-key token)
                    #(roles/admin (config/get-access-control-url system)
                                  token
                                  user-id))
    (catch Exception e
      (log/error e)
      {:errors (base-errors/exception-data e)})))

(defn cached-concept-permission
  "Look up the permissions for a concept in the cache; if there is a miss,
  make the actual call for the lookup."
  [system token user-id concept-id]
  (try
    (caching/lookup system
                    (permissions/permissions-key token concept-id)
                    #(permissions/concept
                      (config/get-access-control-url system)
                      token
                      user-id
                      concept-id))
    (catch Exception e
      (log/error e)
      {:errors (base-errors/exception-data e)})))

(defn check-roles
  "A supporting function for `check-roles-permissions` that handles the roles
  side of things."
  [system handler request route-roles user-token user-id]
  (log/debug "Checking roles annotated in routes ...")
  (let [lookup (cached-admin-role system user-token user-id)
        errors (:errors lookup)]
    (if errors
      (do
        (log/error errors/no-permissions)
        (response/not-allowed errors/no-permissions errors))
      (if (admin-role? route-roles lookup)
        (handler request)
        (response/not-allowed errors/no-permissions)))))

(defn check-permissions
  "A supporting function for `check-roles-permissions` that handles the
  permissions side of things."
  [system handler request route-permissions user-token user-id]
  (let [concept-id (permissions/route-concept-id request)
        lookup (cached-concept-permission
                system user-token user-id concept-id)
        errors (:errors lookup)]
    (log/debug "Checking permissions annotated in routes ...")
    (if errors
      (do
        (log/error errors/no-permissions)
        (response/not-allowed errors/no-permissions errors))
      (if (concept-permission? route-permissions
                               lookup
                               concept-id)
        (handler request)
        (response/not-allowed errors/no-permissions)))))

(defn check-roles-permissions
  "A supporting function for `check-route-access` that handles the actual
  checking."
  [system handler request route-roles route-permissions]
  (if-let [user-token (token/extract request)]
    (let [user-lookup (cached-user system user-token)
          errors (:errors user-lookup)]
      (log/debug "ECHO token provided; proceeding ...")
      (log/trace "user-lookup:" user-lookup)
      (if errors
        (do
          (log/error errors/token-required)
          (response/not-allowed errors/token-required errors))
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Authz [])

(defn start
  [this]
  (log/info "Starting authorization component ...")
  (log/debug "Started authorization component.")
  this)

(defn stop
  [this]
  (log/info "Stopping authorization component ...")
  (log/debug "Stopped authorization component.")
  this)

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Authz
  component/Lifecycle
  lifecycle-behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Constructor   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-component
  ""
  []
  (map->Authz {}))

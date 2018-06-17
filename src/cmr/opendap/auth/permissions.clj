(ns cmr.opendap.auth.permissions
  "Permissions for CMR OPeNDAP are utilized in the application routes when it is
  necessary to limit access to resources based on the specific capabilities
  granted to a user.

  Permissions are included in the route definition along with the route's
  handler. For example:
  ```
  [...
   [\"my/route\" {
    :get {:handler my-handlers/my-route
          :permissions #{:read}}
    :post ...}]
   ...]"
  (:require
   [clojure.set :as set]
   [cmr.authz.permissions :as permissions]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [taoensso.timbre :as log]))

(defn cached-concept
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
      (ex-data e))))

(defn concept?
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

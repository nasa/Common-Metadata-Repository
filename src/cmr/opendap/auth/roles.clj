(ns cmr.opendap.auth.roles
  "Roles for CMR OPeNDAP are utilized in the application routes when it is
  necessary to limit access to resources based on the role of a user.

  Roles are included in the route definition along with the route's handler.
  For example:
  ```
  [...
   [\"my/route\" {
    :get {:handler my-handlers/my-route
          :roles #{:admin}}
    :post ...}]
   ...]"
  (:require
   [clojure.set :as set]
   [cmr.authz.roles :as roles]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.components.config :as config]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(defn cached-admin
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
      {:errors (ex-data e)})))

(defn admin?
  "Check to see if the roles of a given token+user match the required roles for
  the route."
  [route-roles cache-lookup]
  (log/debug "Roles required-set:" route-roles)
  (log/debug "Roles has-set:" cache-lookup)
  (seq (set/intersection cache-lookup route-roles)))

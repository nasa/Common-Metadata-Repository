(ns cmr.authz.permissions
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
   [cmr.authz.acls :as acls]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(def echo-concept-query
  "The query formatter used when making a concept permissions query to the CMR
  Access Control API."
  #(hash-map :concept_id %))

(defn permissions-key
  "Generate a key to be used for caching permissions data."
  [token concept-id]
  (format "permissions:%s:%s" token concept-id))

(defn reitit-acl-data
  "Construct permissions "
  [concept-id annotation]
  (when (and concept-id annotation)
    {concept-id annotation}))

(defn cmr-acl->reitit-acl
  "Convert a CMR ACL to an ACL that can be matched against permissions in the
  reitit routing library's data structure. There following conditions are
  handled:

  * return an empty set when a CMR ACL is nil-valued
  * return a reitit-ready ACL when a map (representing a CMR ACL) is given
  * return the CMR ACL as-is in all other cases."
  [cmr-acl]
  (log/trace "Got CMR ACL:" cmr-acl)
  (cond (nil? cmr-acl)
        #{}

        (map? cmr-acl)
        (->> cmr-acl
             (map (fn [[k v]] [(keyword k) (set (map keyword v))]))
             (into {}))

        :else cmr-acl))

(defn route-concept-id
  "Given a request, return the concept id for which we are checking
  permissions."
  [request]
  (get-in request [:path-params :concept-id]))

(defn- -route-annotation
  [request]
  (get-in (ring/get-match request) [:data :get :permissions]))

(defn route-annotation
  "Extract any permissions annotated in the route associated with the given
  request."
  [request]
  (let [annotation (-route-annotation request)]
    (log/debug "Permissions annotation:" annotation)
    (when annotation
      (reitit-acl-data
       (route-concept-id request)
       (cmr-acl->reitit-acl annotation)))))

(defn concept
  "Query the CMR Access Control API to get the permissions the given token+user
  have for the given concept."
  [base-url token user-id concept-id]
  (let [result @(acls/check-access base-url
                                   token
                                   user-id
                                   (echo-concept-query concept-id))
        errors (:errors result)]
    (if errors
      (throw (ex-info (first errors) result))
      (do
        (log/debug "Got permissions:" result)
        (cmr-acl->reitit-acl result)))))

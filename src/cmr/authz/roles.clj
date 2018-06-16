(ns cmr.authz.roles
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
   [cmr.authz.acls :as acls]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(def management-acl
  "The canonical ingest management ACL definition."
  :INGEST_MANAGEMENT_ACL)

(def echo-management-query
  "The query formatter used when making a roles query to the CMR Access Control
  API. Note that only the management ACL is currently supported, and that this
  maps below to `admin`."
  {:system_object (name management-acl)})

(defn roles-key
  "Generate a key to be used for caching role data."
  [token]
  (str "roles:" token))

(defn cmr-acl->reitit-acl
  [cmr-acl]
  (log/trace "Got CMR ACL:" cmr-acl)
  (if (seq (management-acl cmr-acl))
    #{:admin}
    #{}))

(defn route-annotation
  "Extract any roles annotated in the route associated with the given request."
  [request]
  (get-in (ring/get-match request) [:data :get :roles]))

(defn admin
  "Query the CMR Access Control API to get the roles for the given token+user."
  [base-url token user-id]
  (let [result @(acls/check-access base-url
                                   token
                                   user-id
                                   echo-management-query)
        errors (:errors result)]
    ;; NOTE: Unlike other parts of CMR OPeNDAP, we throw here instead of
    ;;       passing around an error message due to the fact that the
    ;;       caching code has this function burried inside, as acallback.
    (if errors
      (throw (ex-info (first errors) result))
      (do
        (log/debug "Got permissions:" result)
        (cmr-acl->reitit-acl result)))))

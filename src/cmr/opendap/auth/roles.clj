(ns cmr.opendap.auth.roles
  (:require
   [clojure.set :as set]
   [cmr.opendap.auth.acls :as acls]
   [cmr.opendap.components.caching :as caching]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(def management-acl :INGEST_MANAGEMENT_ACL)
(def echo-management-query {:system_object (name management-acl)})

(defn admin-key
  [token]
  (str "admin:" token))

(defn cmr-acl->reitit-acl
  [cmr-acl]
  (if (seq (management-acl cmr-acl))
    #{:admin}
    #{}))

(defn route-annotation
  "It is expected that the route annotation for roles is of the form:

  :METHOD {:handler ...
           :roles #{...}}

  Note that currently, only the :admin role is supported."
  [request]
  (get-in (ring/get-match request) [:data :get :roles]))

(defn admin
  [base-url token user-id]
  (let [perms (acls/check-access base-url
                                 token
                                 user-id
                                 echo-management-query)]
    (cmr-acl->reitit-acl @perms)))

(defn cached-admin
  [system base-url token user-id]
  (caching/lookup system
                  (admin-key token)
                  #(admin base-url token user-id)))

(defn admin?
  [system route-roles base-url token user-id]
  (seq (set/intersection (cached-admin system base-url token user-id)
                         route-roles)))

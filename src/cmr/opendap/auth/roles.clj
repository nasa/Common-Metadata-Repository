(ns cmr.opendap.auth.roles
  (:require
   [clojure.set :as set]
   [cmr.opendap.auth.permissions :as permissions]
   [cmr.opendap.components.caching :as caching]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(def management-acl :INGEST_MANAGEMENT_ACL)

(defn route-annotation
  "It is expected that the route annotation for roles is of the form:

  :METHOD {:handler ...
           :roles #{...}}

  Note that currently, only the :admin role is supported."
  [request]
  (get-in (ring/get-match request) [:data :get :roles]))

(defn admin-key
  [token]
  (str "admin:" token))

(defn admin
  [base-url token user-id]
  (let [perms (permissions/acl base-url
                               token
                               user-id
                               {:system_object (name management-acl)})]
    (if (seq (management-acl @perms))
      #{:admin}
      #{})))

(defn cached-admin
  [system base-url token user-id]
  (caching/lookup system
                  (admin-key token)
                  #(admin base-url token user-id)))

(defn admin?
  [system roles base-url token user-id]
  (seq (set/intersection (cached-admin system base-url token user-id)
                         roles)))

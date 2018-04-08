(ns cmr.opendap.auth.permissions
  (:require
   [clojure.set :as set]
   [cmr.opendap.auth.acls :as acls]
   [cmr.opendap.components.caching :as caching]
   [reitit.ring :as ring]
   [taoensso.timbre :as log]))

(def echo-concept-query #(hash-map :concept_id %))

(defn concept-key
  [token]
  (str "concept:" token))

(defn reitit-acl-data
  [concept-id annotation]
  (when (and concept-id annotation)
    {concept-id annotation}))

(defn cmr-acl->reitit-acl
  [cmr-acl]
  (log/debug "Got CMR ACL:" cmr-acl)
  (cond (nil? cmr-acl) #{}
        :else cmr-acl))

(defn route-concept-id
  [request]
  (get-in request [:path-params :concept-id]))

(defn route-annotation
  "It is expected that the route annotation for permissions is of the form:

  :METHOD {:handler ...
           :permissions #{...}}

  Supported elements of the :permissions set is currently just :read."
  [request]
  (log/trace "Request:" request)
  (reitit-acl-data
   (route-concept-id request)
   (get-in (ring/get-match request) [:data :get :permissions])))

(defn concept
  [base-url token user-id concept-id]
  (let [perms (acls/check-access base-url
                                 token
                                 user-id
                                 (echo-concept-query concept-id))]
    (cmr-acl->reitit-acl @perms)))

(defn cached-concept
  [system base-url token user-id concept-id]
  (caching/lookup system
                  (concept-key token)
                  #(concept base-url token user-id concept-id)))

(defn concept?
  [system roles base-url token user-id concept-id]
  (seq (set/intersection (cached-concept system
                                         base-url
                                         token
                                         user-id
                                         concept-id)
                         roles)))

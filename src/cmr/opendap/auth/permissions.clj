(ns cmr.opendap.auth.permissions
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def permissions-resource "/access-control/permissions")
(def management-acl :INGEST_MANAGEMENT_ACL)

(defn admin-key
  [token]
  (str "admin:" token))

(defn parse-acl-permissions
  [data]
  (let [str-data (slurp data)
        json-data (json/parse-string str-data true)]
    (log/debug "str-data:" str-data)
    (log/debug "json-data:" json-data)
    json-data))

(defn acl
  [base-url token user-id acl-query]
  (let [url (str base-url permissions-resource)]
    (httpc/request (-> request/default-options
                       (request/options
                        :method :get
                        :url url
                        :query-params (merge
                                       {:user_id user-id}
                                       acl-query))
                       (request/add-token-header token)
                       (request/add-client-id)
                       ((fn [x] (log/trace "Prepared request:" x) x)))
                    #(response/client-handler % parse-acl-permissions))))

(defn admin
  [base-url token user-id]
  (let [perms (acl
               base-url
               token
               user-id
               {:system_object (name management-acl)})]
    (if (seq (management-acl @perms))
      #{:admin}
      #{})))

(defn cached-admin
  [system base-url token user-id]
  (caching/lookup
   system
   (admin-key token)
   #(admin base-url token user-id)))

(defn admin?
  [system roles base-url token user-id]
  (seq (set/intersection (cached-admin system base-url token user-id)
                         roles)))

(defn concept?
  [base-url token user-id concept-id]
  (acl base-url token user-id {:concept_id concept-id}))

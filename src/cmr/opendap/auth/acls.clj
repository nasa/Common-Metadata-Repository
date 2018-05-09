(ns cmr.opendap.auth.acls
  "This namespace is provided for common code needed by the roles and
  permissions namespaces."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def permissions-resource
  "The path segment to the CMR Access Control API resource that is queried
  in order to get user permissions."
  "/permissions")

(defn check-access
  "This function is responsible for making a call to the CMR Access Control
  permissions resource to check what has been granted for the given user."
  [base-url token user-id acl-query]
  (let [url (str base-url permissions-resource)
        req {:query-params (merge {:user_id user-id}
                                  acl-query)}]
    (request/async-get
     url
     (request/add-token-header req token)
     response/json-handler)))

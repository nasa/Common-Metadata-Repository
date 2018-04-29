(ns cmr.opendap.auth.acls
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def permissions-resource "/permissions")

(defn check-access
  [base-url token user-id acl-query]
  (let [url (str base-url permissions-resource)
        req {:query-params (merge {:user_id user-id}
                                  acl-query)}]
    (request/async-get
     url
     (request/add-token-header req token)
     response/json-handler)))

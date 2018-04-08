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

(defn concept?
  [base-url token user-id concept-id]
  (acl base-url token user-id {:concept_id concept-id}))

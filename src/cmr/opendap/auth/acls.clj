(ns cmr.opendap.auth.acls
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.opendap.components.caching :as caching]
   [cmr.opendap.http.request :as request]
   [cmr.opendap.http.response :as response]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def acl-resource "/access-control/permissions")

(defn parse-response
  [data]
  (let [str-data (slurp data)
        json-data (json/parse-string str-data true)]
    (log/debug "str-data:" str-data)
    (log/debug "json-data:" json-data)
    json-data))

(defn check-access
  [base-url token user-id acl-query]
  (let [url (str base-url acl-resource)]
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
                    #(response/client-handler % parse-response))))

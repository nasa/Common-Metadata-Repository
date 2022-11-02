(ns cmr.authz.http
  (:require
    [cmr.authz.const :as const]
    [cmr.http.kit.request :as request]
    [taoensso.timbre :as log]))

(defn add-user-agent
  ([]
    (add-user-agent {}))
  ([req]
    (request/add-header req "User-Agent" const/user-agent)))

(defn add-client-id
  ([]
    (add-client-id {}))
  ([req]
    (request/add-header req "Client-Id" const/client-id)))

(defn add-options
  [req]
  (-> req
      (assoc :user-agent const/user-agent
             :insecure? true)
      (add-user-agent)
      (add-client-id)))

(ns cmr.opendap.http.request
  (:require
   [cmr.opendap.const :as const]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log]))

(def default-options
  {:user-agent const/user-agent
   :insecure? true})

(defn options
  [request & opts]
  (apply assoc (concat [request] opts)))

(defn get-header
  [request field]
  (get-in request [:headers field]))

(defn add-header
  [request field value]
  (assoc-in request [:headers field] value))

(defn add-token-header
  [request token]
  (add-header request "Echo-Token" token))

(defn add-form-ct
  [request]
  (add-header request "Content-Type" "application/x-www-form-urlencoded"))

(defn add-client-id
  [request]
  (add-header request "Client-Id" "cmr-opendap-token-checker"))



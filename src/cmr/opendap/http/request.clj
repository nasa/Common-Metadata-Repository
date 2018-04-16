(ns cmr.opendap.http.request
  (:require
   [cmr.opendap.const :as const]
   [org.httpkit.client :as httpc]
   [taoensso.timbre :as log])
  (:refer-clojure :exclude [get]))

(def default-options
  {:user-agent const/user-agent
   :insecure? true})

(defn options
  [req & opts]
  (apply assoc (concat [req] opts)))

(defn get-header
  [req field]
  (get-in req [:headers field]))

(defn add-header
  [req field value]
  (assoc-in req [:headers field] value))

(defn add-token-header
  ([token]
    (add-token-header {} token))
  ([req token]
    (add-header req "Echo-Token" token)))

(defn add-user-agent
  [req]
  (add-header req "User-Agent" const/user-agent))

(defn add-form-ct
  [req]
  (add-header req "Content-Type" "application/x-www-form-urlencoded"))

(defn add-client-id
  [req]
  (add-header req "Client-Id" "cmr-opendap-token-checker"))

(defn request
  [method url req]
  (httpc/request (-> default-options
                     (add-client-id)
                     (add-user-agent)
                     (merge req)
                     (assoc :url url :method method))))

(defn async-get
  ([url]
    (async-get url {}))
  ([url req]
    (request :get url req)))

(defn async-post
  ([url]
    (async-post url {:body nil}))
  ([url req]
    (request :post url req)))

(defn get
  [& args]
  @(apply async-get args))

(defn post
  [& args]
  @(apply async-post args))

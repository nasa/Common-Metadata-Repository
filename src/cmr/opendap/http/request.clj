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
  ([field value]
    (add-header {} field value))
  ([req field value]
    (assoc-in req [:headers field] value)))

(defn add-accept
  ([value]
    (add-accept {} value))
  ([req value]
    (add-header req "Accept" value)))

(defn add-token-header
  ([token]
    (add-token-header {} token))
  ([req token]
    (add-header req "Echo-Token" token)))

(defn add-user-agent
  ([]
    (add-user-agent {}))
  ([req]
    (add-header req "User-Agent" const/user-agent)))

(defn add-form-ct
  ([]
    (add-form-ct {}))
  ([req]
    (add-header req "Content-Type" "application/x-www-form-urlencoded")))

(defn add-client-id
  ([]
    (add-client-id {}))
  ([req]
    (add-header req "Client-Id" const/client-id)))

(defn add-payload
  ([data]
    (add-payload {} data))
  ([req data]
    (assoc req :body data)))

(defn request
  [method url req & [callback]]
  (httpc/request (-> default-options
                     (add-client-id)
                     (add-user-agent)
                     (merge req)
                     (assoc :url url :method method))
                  callback))

(defn async-get
  ([url]
    (async-get url {}))
  ([url req]
    (async-get url req nil))
  ([url req callback]
    (request :get url req callback)))

(defn async-post
  ([url]
    (async-post url {:body nil}))
  ([url req]
    (async-post url req nil))
  ([url req callback]
    (request :post url req callback)))

(defn get
  [& args]
  @(apply async-get args))

(defn post
  [& args]
  @(apply async-post args))

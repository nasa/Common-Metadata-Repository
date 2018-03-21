(ns cmr.graph.rest.response
  (:require
   [cheshire.core :as json]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]))

(defn ok
  [_request & args]
  (response/ok args))

(defn json
  [_request data]
  (-> data
      json/generate-string
      response/ok
      (response/content-type "application/json")))

(defn not-found
  [_request]
  (response/content-type
   (response/not-found "Not Found")
   "plain/text"))

(defn cors
  [request response]
  (log/debug "Got request:" request)
  (log/debug "Got response:" response)
  (log/debug "Got request-method:" (:request-method request))
  (if (= :options (:request-method request))
    (do
      (log/debug "Setting CORS response headers ...")
      (-> response
          (response/content-type "text/plain; charset=utf-8")
          (response/header "Access-Control-Allow-Origin" "*")
          (response/header "Access-Control-Allow-Methods" "POST, PUT, GET, DELETE, OPTIONS")
          (response/header "Access-Control-Allow-Headers" "Content-Type")
          (response/header "Access-Control-Max-Age" "2592000")
          ((fn [x] (log/debug "New response:" x) x))))
    response))

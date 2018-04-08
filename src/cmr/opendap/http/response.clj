(ns cmr.opendap.http.response
  "This namespace defines a default set of transform functions suitable for use
  in presenting results to HTTP clients.

  Note that ring-based middleeware may take advantage of these functions either
  by single use or composition."
  (:require
   [cheshire.core :as json]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]))

(defn client-handler
  [{:keys [status headers body error]} parse-fn]
  (log/debug "Handling client response ...")
  (cond error
        (log/error error)
        (>= status 400)
        (do
          (log/error status)
          (log/debug "Headers:" headers)
          (log/debug "Body:" body))
        :else
        (parse-fn body)))

(defn ok
  [_request & args]
  (response/ok args))

(defn json
  [_request data]
  (-> data
      json/generate-string
      response/ok
      (response/content-type "application/json")))

(defn text
  [_request data]
  (-> data
      response/ok
      (response/content-type "text/plain")))

(defn not-found
  [_request]
  (response/content-type
   (response/not-found "Not Found")
   "text/plain"))

(defn not-allowed
  [data]
  (-> data
      response/forbidden
      (response/content-type "text/plain")))

(defn cors
  [request response]
  (case (:request-method request)
    :options (-> response
                 (response/content-type "text/plain; charset=utf-8")
                 (response/header "Access-Control-Allow-Origin" "*")
                 (response/header "Access-Control-Allow-Methods" "POST, PUT, GET, DELETE, OPTIONS")
                 (response/header "Access-Control-Allow-Headers" "Content-Type")
                 (response/header "Access-Control-Max-Age" "2592000"))
    (response/header response "Access-Control-Allow-Origin" "*")))

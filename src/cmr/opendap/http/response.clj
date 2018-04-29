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
  ([response]
    (client-handler response identity))
  ([{:keys [status headers body error]} parse-fn]
    (log/debug "Handling client response ...")
    (cond error
          (log/error error)
          (>= status 400)
          (do
            (log/error status)
            (log/debug "Headers:" headers)
            (log/debug "Body:" body))
          :else
          (parse-fn body))))

(defn parse-json-body
  [body]
  (let [str-data (if (string? body) body (slurp body))
        json-data (json/parse-string str-data true)]
    (log/debug "str-data:" str-data)
    (log/debug "json-data:" json-data)
    json-data))

(def json-handler #(client-handler % parse-json-body))

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

(defn html
  [_request data]
  (-> data
      response/ok
      (response/content-type "text/html")))

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

(ns cmr.opendap.http.response
  "This namespace defines a default set of transform functions suitable for use
  in presenting results to HTTP clients.

  Note that ring-based middleeware may take advantage of these functions either
  by single use or composition."
  (:require
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [ring.util.http-response :as response]
   [taoensso.timbre :as log]
   [xml-in.core :as xml-in]))

(defn parse-json-body
  [body]
  (let [str-data (if (string? body) body (slurp body))
        json-data (json/parse-string str-data true)]
    (log/trace "str-data:" str-data)
    (log/trace "json-data:" json-data)
    json-data))

(defn json-errors
  [body]
  (:errors (parse-json-body body)))

(defn parse-xml-body
  [body]
  (let [str-data (if (string? body) body (slurp body))]
    (xml/parse-str str-data)))

(defn xml-errors
  [body]
  (vec (xml-in/find-all (parse-xml-body body)
                        [:errors :error])))

(defn error-handler
  [status headers body]
  (log/error "HTTP Error status code:" status)
  (log/trace "Headers:" headers)
  (log/trace "Body:" body)
  (cond (string/starts-with? (:content-type headers)
                             "application/xml")
        (let [errs (xml-errors body)]
          (log/error errs)
          {:errors errs})

        (string/starts-with? (:content-type headers)
                             "application/json")
        (let [errs (json-errors body)]
          (log/error errs)
          {:errors errs})

        :else
        body))

(defn client-handler
  ([response]
    (client-handler response identity))
  ([{:keys [status headers body error]} parse-fn]
    (log/debug "Handling client response ...")
    (cond error
          (do
            (log/error error)
            {:errors [error]})

          (>= status 400)
          (error-handler status headers body)

          :else
          (parse-fn body))))

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

(defn errors
  [errors]
  {:errors errors})

(defn error
  [error]
  (errors [error]))

(defn not-allowed
  ([message]
   (not-allowed message []))
  ([message other-errors]
   (-> (conj other-errors message)
       errors
       json/generate-string
       response/forbidden
       (response/content-type "application/json"))))

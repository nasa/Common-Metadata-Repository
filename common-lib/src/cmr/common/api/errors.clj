(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]
            [cmr.common.api :as api]
            [cmr.common.services.errors :as errors]
            [clojure.data.xml :as x]
            [clojure.string :as str]
            [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [cmr.common.mime-types :as mt]
            [cmr.common.config :as cfg]))

(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :unauthorized 401
   :invalid-data 422
   :conflict 409
   :service-unavailable 503})

(def CONTENT_TYPE_HEADER "Content-Type")
(def CORS_ORIGIN_HEADER "Access-Control-Allow-Origin")

(def internal-error-ring-response
  {:status 500
   :headers {CONTENT_TYPE_HEADER :json
             CORS_ORIGIN_HEADER "*"}
   :body {:errors ["An Internal Error has occurred."]}})

(defn- keyword-path->string-path
  "Converts a set of keyword field paths into the string equivalent field paths to return to the
  user."
  [field-path]
  (map (fn [path-item]
         (if (number? path-item)
           path-item
           (csk/->PascalCaseString path-item)))
       field-path))

(defmulti errors->body-string
  "Converts a set of errors into a string to return in the response body formatted according
  to the requested response format."
  (fn [response-format errors pretty?]
    response-format))

(defmulti error->json-element
  "Converts an individual error element to a clojure data structure representing the JSON element."
  (fn [error]
    (type error)))

(defmethod error->json-element String
  [error]
  error)

(defmethod error->json-element cmr.common.services.errors.PathErrors
  [error]
  (update-in error [:path] keyword-path->string-path))

(defmethod errors->body-string :json
  [response-format errors pretty?]
  (json/generate-string {:errors (map error->json-element errors)} {:pretty pretty?}))

(defmulti error->xml-element
  "Converts an individual error element to the equivalent XML structure."
  (fn [error]
    (type error)))

(defmethod error->xml-element String
  [error]
  (x/element :error {} error))

(defmethod error->xml-element cmr.common.services.errors.PathErrors
  [error]
  (let [{:keys [path errors]} error]
    (x/element :error {}
               (x/element :path {} (str/join "/" (keyword-path->string-path path)))
               (x/element :errors {}
                          (for [error errors]
                            (x/element :error {} error))))))

(defmethod errors->body-string :xml
  [response-format errors pretty?]
  (let [xml-fn (if pretty? x/indent-str x/emit-str)]
    (xml-fn
      (x/element :errors {}
                 (map error->xml-element errors)))))

(defn- response-type-body
  "Returns the response content-type and body for the given errors and format"
  [errors results-format pretty?]
  (let [content-type (if (re-find #"xml" results-format) "application/xml" "application/json")
        response-format (mt/mime-type->format content-type)
        body (errors->body-string response-format errors pretty?)]
    [content-type body]))

(defn- handle-service-error
  "Handles service errors thrown during a request and returns the appropriate ring response."
  [default-format-fn request type errors e]
  (let [results-format (mt/get-results-format
                         (:uri request)
                         (:headers request)
                         (default-format-fn request))
        status-code (type->http-status-code type)
        [content-type response-body] (response-type-body errors results-format
                                                         (api/pretty-request? request))]
    ;; Log exceptions for server errors
    (when (>= status-code 500)
      (error e))
    {:status status-code
     :headers {CONTENT_TYPE_HEADER content-type
               CORS_ORIGIN_HEADER "*"}
     :body response-body}))

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions. The default-format-fn is a function which determines in what format to return an error
  if the request does not explicitly set a format.  It takes the request as an argument."
  ([f]
   (exception-handler f (constantly "application/json")))
  ([f default-format-fn]
   (fn [request]
     (try
       (errors/handle-service-errors
         (partial f request)
         (partial handle-service-error default-format-fn request))
       (catch Throwable e
         (error e)
         internal-error-ring-response)))))

(defn invalid-url-encoding-handler
  "Detect invalid encoding in the url and throws a 400 error. Ring default handling simply converts
  the invalid encoded parameter value to nil and causes 500 error later during search (see CMR-1192).
  This middleware handler returns a 400 error early to avoid the 500 error."
  [f]
  (fn [request]
    (try
      (when-let [query-string (:query-string request)]
        (java.net.URLDecoder/decode query-string "UTF-8"))
      (catch Exception e
        (errors/throw-service-error
          :bad-request
          (str "Invalid URL encoding: " (str/replace (.getMessage e) #"URLDecoder: " "")))))
    (f request)))

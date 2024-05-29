(ns cmr.common.api.errors
  "Functions for throwing errors in a web service."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.common.log :refer [error info]]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]))

(def type->http-status-code
  "mappings for standard non-successfull http status codes"
  {:bad-request 400
   :unauthorized 401
   :not-found 404
   :conflict 409
   :payload-too-large 413
   :invalid-content-type 415
   :invalid-data 422
   :too-many-requests 429
   :service-unavailable 503
   :gateway-timeout 504})

(def CONTENT_TYPE_HEADER "An HTTP Header" "Content-Type")
(def CORS_ORIGIN_HEADER "An HTTP HEADER" "Access-Control-Allow-Origin")

(def internal-error-ring-response
  "A basic 500 response"
  {:status 500
   :headers {CONTENT_TYPE_HEADER mt/json
             CORS_ORIGIN_HEADER "*"}
   :body {:errors ["An Internal Error has occurred."]}})

(defn mask-token-error
  "Replace real tokens with a message"
  [error-string]
  (if (re-matches #".*Token .* does not exist.*" error-string)
    "Token does not exist"
    error-string))

(defn- keyword-path->string-path
  "Converts a set of keyword field paths into the string equivalent field paths
  to return to the user."
  [field-path]
  (map (fn [path-item]
         (if (number? path-item)
           path-item
           (csk/->PascalCaseString path-item)))
       field-path))

;; *************************************

(defmulti error->json-element
  "Converts an individual error element to a clojure data structure
  representing the JSON element."
  type)

(defmethod error->json-element String
  [error]
  (mask-token-error error))

(defmethod error->json-element cmr.common.services.errors.PathErrors
  [error]
  (update-in error [:path] keyword-path->string-path))

;; *************************************

(defmulti error->xml-element
  "Converts an individual error element to the equivalent XML structure."
  type)

(defmethod error->xml-element String
  [error]
  (xml/element :error {} (mask-token-error error)))

(defmethod error->xml-element cmr.common.services.errors.PathErrors
  [error]
  (let [{:keys [path errors]} error]
    (xml/element
      :error {}
      (xml/element
        :path {} (string/join "/" (keyword-path->string-path path)))
      (xml/element
        :errors {} (for [error errors] (xml/element :error {} error))))))

;; *************************************

(defmulti errors->body-string
  "Converts a set of errors into a string to return in the response body
  formatted according to the requested response format."

  (fn [response-format _errors]
    response-format))

(defmethod errors->body-string mt/json
  [_ errors]
  (json/generate-string {:errors (map error->json-element errors)}))

(defmethod errors->body-string mt/xml
  [_ errors]
  (xml/emit-str
   (xml/element :errors {}
                (map error->xml-element errors))))

;; *************************************

(defn- response-type-body
  "Returns the response content-type and body for the given errors and format."
  [errors results-format]
  (let [content-type (if (re-find #"xml" results-format) mt/xml mt/json)
        body (errors->body-string content-type errors)]
    [content-type body]))

(defn- get-results-format
  "Returns the requested results format parsed from the URL extension.If the
  URL extension does not designate the format, then determine the mime-type
  from the accept and content-type headers. If the format still cannot be
  determined return the default-mime-type as passed in."
  ([path-w-extension headers default-mime-type]
   (get-results-format
     path-w-extension headers mt/all-supported-mime-types default-mime-type))
  ([path-w-extension headers valid-mime-types default-mime-type]
   (or (mt/path->mime-type path-w-extension)
       (mt/accept-mime-type headers valid-mime-types)
       (mt/content-type-mime-type headers valid-mime-types)
       default-mime-type)))

(defn handle-service-error
  "Handles service errors thrown during a request and returns the appropriate
  ring response."
  [default-format-fn request e-type errors e]
  (let [results-format (get-results-format
                         (:uri request)
                         (:headers request)
                         (default-format-fn request e))
        status-code (type->http-status-code e-type)
        [content-type response-body] (response-type-body
                                      errors results-format)]
    ;; Log exceptions for server errors
    (if (>= status-code 500)
      (error e)
      (info "Failed with status code ["
            status-code
            "], response body: "
            response-body))

    {:status status-code
     :headers {CONTENT_TYPE_HEADER content-type
               CORS_ORIGIN_HEADER "*"}
     :body response-body}))

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the
  cmr.common.services.errors functions. The default-format-fn is a function
  which determines in what format to return an error if the request does not
  explicitly set a format.It takes the request and the ExceptionInfo as
  arguments."
  ([f]
   (exception-handler f (constantly mt/json)))
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
  "Detect invalid encoding in the url and throws a 400 error. Ring default
  handling simply converts the invalid encoded parameter value to nil and
  causes 500 error later during search (see CMR-1192). This middleware handler
  returns a 400 error early to avoid the 500 error."
  [f]
  (fn [request]
    (try
      (when-let [query-string (:query-string request)]
        (java.net.URLDecoder/decode query-string "UTF-8"))
      (catch Exception e
        (errors/throw-service-error
          :bad-request
          (str "Invalid URL encoding: "
               (string/replace (.getMessage e) #"URLDecoder: " "")))))
    (f request)))

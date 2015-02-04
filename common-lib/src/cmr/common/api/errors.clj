(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]
            [cmr.common.services.errors :as errors]
            [clojure.data.xml :as x]
            [camel-snake-kebab :as csk]
            [cheshire.core :as json]))

(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :unauthorized 401
   :invalid-data 422
   :conflict 409})

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
           (str path-item)
           (csk/->CamelCaseString path-item)))
       field-path))

(defmulti errors->body-string
  "Converts a set of errors into a string to return in the response body formatted according
  to the requested response format."
  (fn [response-format errors]
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
  [response-format errors]
  ;; TODO support pretty
  (json/generate-string {:errors (map error->json-element errors)}))

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
               (x/element :path {} (keyword-path->string-path path))
               (x/element :errors {}
                          (for [error errors]
                            (x/element :error {} error))))))


;; TODO support pretty. We can get this out of the request object.
;; use capture to examine it

(defmethod errors->body-string :xml
  [response-format errors]
  (x/emit-str
    (x/element :errors {}
               (map error->xml-element errors))))

(defn- response-type-body
  "Returns the response content-type and body for the given errors and format"
  [errors xml-format?]
  (let [content-type (if xml-format? "application/xml" "application/json")
        response-format (if xml-format? :xml :json)
        body (errors->body-string response-format errors)]
    [content-type body]))

(defn- handle-exception-info
  "Handles a Clojure ExceptionInfo instance that was caught."
  [request e]
  (let [data (ex-data e)]
    (if (:type data)
      (let [accept-format (get-in request [:headers "accept"])
            ;; TODO Chris fix this
            xml-format? (when accept-format (re-find #"xml" accept-format))
            {:keys [type errors]} data
            status-code (type->http-status-code type)
            [content-type response-body] (response-type-body errors xml-format?)]
        {:status status-code
         :headers {CONTENT_TYPE_HEADER content-type
                   CORS_ORIGIN_HEADER "*"}
         :body response-body})
      (do
        (error e)
        internal-error-ring-response))))

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions."
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        (handle-exception-info request e))
      (catch Throwable e
        (error e)
        internal-error-ring-response))))

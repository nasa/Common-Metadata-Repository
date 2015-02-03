(ns cmr.common.api.errors
  (:require [cmr.common.log :refer [error]]
            [clojure.data.xml :as x]
            [cmr.common.mime-types :as mt]
            [cmr.common.config :as cfg]))

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

(defn- response-type-body
  "Returns the response content-type and body for the given errors and format"
  [errors xml-format?]
  (let [content-type (if xml-format? "application/xml" "application/json")
        body (if xml-format?
               (x/emit-str
                 (x/element :errors {}
                            (for [err errors]
                              (x/element :error {} err))))
               {:errors errors})]
    [content-type body]))

(defn exception-handler
  "A ring exception handler that will handle errors thrown by the cmr.common.services.errors
  functions."
  [f]
  (fn [request]
    (try (f request)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (:type data)
            (let [results-format (mt/get-results-format
                                   (:uri request)
                                   (:headers request)
                                   ;; For the search application most routes default to XML format.
                                   ;; All other applications default to json format.  Note that
                                   ;; this approach to determining the correct default for a given
                                   ;; route is brittle and could be difficult to maintain.
                                   (if (and (=
                                              (cfg/config-value
                                                :search-port
                                                3003
                                                (fn [s] (Long. s)))
                                              (:server-port request))
                                            (not (re-find #"caches" (:uri request))))
                                     "application/xml"
                                     "application/json"))
                  xml-format? (when results-format (re-find #"xml" results-format))
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
      (catch Throwable e
        (error e)
        internal-error-ring-response))))

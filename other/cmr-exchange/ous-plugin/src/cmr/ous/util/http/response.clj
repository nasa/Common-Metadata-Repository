(ns cmr.ous.util.http.response
  "This namespace defines a default set of transform functions suitable for use
  in presenting results to HTTP clients.

  Note that ring-based middleeware may take advantage of these functions either
  by single use or composition."
  (:require
   [cheshire.core :as json]
   [cheshire.generate :as json-gen]
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [cmr.authz.errors :as authz-errors]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.http.kit.response :as response]
   [cmr.ous.results.errors :as ous-errors]
   [ring.util.http-response :as ring-response]
   [taoensso.timbre :as log]
   [xml-in.core :as xml-in])
  (:refer-clojure :exclude [error-handler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Backwards-compatible Aliases   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def parse-json-result response/parse-json-result)
(def json-errors response/json-errors)
(def parse-xml-body response/parse-xml-body)
(def xml-errors response/xml-errors)
(def ok response/ok)
(def not-found response/not-found)
(def cors response/cors)
(def add-header response/add-header)
(def version-media-type response/version-media-type)
(def errors response/errors)
(def error response/error)
(def not-allowed response/not-allowed)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Custom Response Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn error-handler
  [status headers body]
  (response/error-handler status headers body (format errors/status-code status)))

(defn general-response-handler
  "Handles the response and returns body and headers"
  ([response]
   (general-response-handler response identity))
  ([response parse-fn]
   (response/general-response-handler response error-handler parse-fn)))

(defn body-only-response-handler
  "Returns only the body from a response"
  ([response]
   (body-only-response-handler response identity))
  ([response parse-fn]
   (response/body-only-response-handler response error-handler parse-fn)))

(defn headers-only-response-handler
  "Returns only the headers from a response"
  ([response]
   (body-only-response-handler response identity))
  ([response parse-fn]
   (response/headers-only-response-handler response error-handler parse-fn)))

;; Wrapped for JSON specific use
(def general-json-handler #(general-response-handler % response/parse-json-result))
(def body-json-handler #(body-only-response-handler % response/parse-json-result))
(def headers-json-handler #(headers-only-response-handler % response/parse-json-result))

(defn process-err-results
  [data]
  (cond (authz-errors/any-errors? data)
        {:status authz-errors/error-code}

        (errors/any-server-errors? ous-errors/status-map data)
        {:status errors/server-error-code}

        (errors/any-client-errors? ous-errors/status-map data)
        {:status errors/client-error-code}

        :else
        {:status errors/default-error-code}))

(defn process-results
  [data]
  (response/process-results process-err-results data))

(defn json
  [request data]
  (response/json request process-results data))

(defn text
  [request data]
  (response/text request process-results data))

(defn html
  [request data]
  (response/html request process-results data))

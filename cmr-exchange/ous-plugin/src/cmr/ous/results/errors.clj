(ns cmr.ous.results.errors
  (:require
   [clojure.set :as set]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]
   [cmr.metadata.proxy.results.errors :as metadata-errors]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Messages   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Authorization

(def no-permissions "You do not have permissions to access that resource.")
(def token-required "An ECHO token is required to access this resource.")

;; OUS - General

(def unsupported-processing-level
  "The requst includes a dataset whose processing level is not supported.")

(def problem-processing-level
  "Problematic processing level %s for collection %s.")

;; OUS - Parameters

(def missing-collection-id
  "The provided parameters are missing the required field 'collection-id'.")

(def invalid-lat-params
  (str "The values provided for latitude are not within the valid range of "
       "-90 degrees through 90 degrees."))

(def invalid-lon-params
  (str "The values provided for longitude are not within the valid range of "
       "-180 degrees through 180 degrees."))

;; OUS - Results

(def empty-query-string
  "No OPeNDAP query string was generated for the request.")

(def client-errors-set
  (get metadata-errors/status-map errors/client-error-code))

(def server-errors-set
  (get metadata-errors/status-map errors/server-error-code))

(def status-map
  "This is a lookup data structure for how HTTP status/error codes map to CMR
  OPeNDAP errors."
  (util/deep-merge
    errors/status-map
    metadata-errors/status-map
    {errors/client-error-code #{client-errors-set}}))

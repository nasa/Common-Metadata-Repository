(ns cmr.opendap.results.errors
  (:require
   [clojure.set :as set]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]))

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

;; OUS - CMR Metadata

(def problem-granules
  "Problematic granules: [%s].")

(def empty-svc-pattern
  (str "The service pattern computed was empty. Is there a service associated "
       "with the given collection? Does the UMM-S record in question have "
       "values for the pattern fields?"))

(def empty-gnl-data-file-url
  (str "There was a problem extracting a data URL from the granule's service "
       "data file."))

(def empty-gnl-data-files
  "There was a problem extracting a service data file from the granule.")

(def no-matching-service-pattern
  (str "There was a problem creating URLs from granule file data: couldn't "
       "match any default service patterns (i.e.: %s) to service %s."))

(def granule-metadata
  "There was a problem extracting granule metadata.")

(def service-metadata
  "There was a problem extracting service metadata.")

(def variable-metadata
  "There was a problem extracting variable metadata.")

;; OUS - Results

(def empty-query-string
  "No OPeNDAP query string was generated for the request.")

(def status-map
  "This is a lookup data structure for how HTTP status/error codes map to CMR
  OPeNDAP errors."
  (util/deep-merge
    errors/status-map
    {errors/client-error-code #{empty-svc-pattern
                                invalid-lat-params
                                invalid-lon-params
                                unsupported-processing-level
                                problem-processing-level}
     errors/server-error-code #{empty-gnl-data-files
                                ;;empty-gnl-data-file-url
                                problem-granules
                                no-matching-service-pattern
                                granule-metadata
                                service-metadata
                                variable-metadata}}))

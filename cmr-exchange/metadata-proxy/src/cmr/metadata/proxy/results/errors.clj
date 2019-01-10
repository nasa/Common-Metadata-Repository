(ns cmr.metadata.proxy.results.errors
  (:require
   [clojure.set :as set]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.exchange.common.util :as util]))

(def problem-granules
  "Problematic granules: [%s].")

(def empty-svc-pattern
  (str "The service pattern computed was empty. Is there a service associated "
       "with the given collection? Does the UMM-S record in question have "
       "values for the pattern fields?"))

(def empty-gnl-data-file-url
  (str "There was a problem extracting a data URL from the granule's "
       "metadata file."))

(def empty-granule-links
  (str "There was a problem extracting an OPeNDAP URL or data URL from the "
       "granule's metadata file."))

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

(def status-map
  "This is a lookup data structure for how HTTP status/error codes map to CMR
  OPeNDAP errors."
  (util/deep-merge
    errors/status-map
    {errors/client-error-code #{}
     errors/server-error-code #{empty-gnl-data-files
                                ;;empty-gnl-data-file-url
                                problem-granules
                                no-matching-service-pattern
                                granule-metadata
                                service-metadata
                                variable-metadata}}))

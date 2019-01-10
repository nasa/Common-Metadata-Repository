(ns cmr.exchange.common.results.errors
  (:require
   [clojusc.results.errors :as errors]
   [clojusc.results.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Defaults   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-error-code errors/default-error-code)
(def client-error-code errors/client-error-code)
(def server-error-code errors/server-error-code)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Messages   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Generic

(def status-code
  "HTTP Error status code: %s.")

(def not-implemented
  "This capability is not currently implemented.")

(def unsupported
  "This capability is not currently supported.")

(def invalid-parameter
  "One or more of the parameters provided were invalid.")

(def missing-parameters
  "The following required parameters are missing from the request:")

(def status-map
  "This is a lookup data structure for how HTTP status/error codes map to CMR
  OPeNDAP errors."
  {client-error-code #{not-implemented
                       unsupported
                       invalid-parameter
                       missing-parameters}
   server-error-code #{}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Handling API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def any-client-errors? #'errors/any-client-errors?)
(def any-server-errors? #'errors/any-server-errors?)
(def check #'errors/check)
(def exception-data #'errors/exception-data)
(def get-errors #'errors/get-errors)
(def erred? #'errors/erred?)
(def any-erred? #'errors/any-erred?)
(def collect #'errors/collect)

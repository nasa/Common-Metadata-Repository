(ns cmr.opendap.errors
  (:require
   [clojure.set :as set]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Defaults   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-error-code 400)
(def auth-error-code 403)
(def client-error-code 400)
(def server-error-code 500)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Messages   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Authorization

(def no-permissions "You do not have permissions to access that resource.")
(def token-required "An ECHO token is required to access this resource.")

;; Generic

(def status-code
  "HTTP Error status code: %s")

;; OUS - General

(def not-implemented
  "This capability is not currently implemented.")

(def unsupported
  "This capability is not currently supported.")

;; OUS - Parameters

(def invalid-lat-params
  (str "The values provided for latitude are not within the valid range of "
       "-90 degrees through 90 degress."))

(def invalid-lon-params
  (str "The values provided for longitude are not within the valid range of "
       "-180 degrees through 180 degress."))

;; OUS - CMR Metadata

(def empty-svc-pattern
  (str "The service pattern computed was empty. Is there a service associated "
       "with the given collection? Does the UMM-S record in question have "
       "values for the pattern fields?"))

(def empty-gnl-data-files
  "There was a problem extracting a service data file from the granule.")

(def no-matching-service-pattern
  (str "There was a problem creating URLs from granule file data: couldn't "
       "match default service pattern %s to service %s."))

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
  {client-error-code #{empty-svc-pattern
                       invalid-lat-params
                       invalid-lon-params}
   auth-error-code #{no-permissions
                     token-required}
   server-error-code #{empty-gnl-data-files
                       no-matching-service-pattern
                       granule-metadata
                       service-metadata
                       variable-metadata}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Handling API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn any-auth-errors?
  [errors]
  (seq (set/intersection (get status-map auth-error-code)
                         (set (:errors errors)))))

(defn any-client-errors?
  [errors]
  (seq (set/intersection (get status-map client-error-code)
                         (set (:errors errors)))))

(defn any-server-errors?
  [errors]
  (seq (set/intersection (get status-map server-error-code)
                         (set (:errors errors)))))

(defn check
  ""
  [& msgs]
  (remove nil? (map (fn [[check-fn value msg]] (when (check-fn value) msg))
                    msgs)))

(defn get-errors
  [data]
  (or (:errors data)
      (when-let [error (:error data)]
        [error])))

(defn erred?
  ""
  [data]
  (seq (get-errors data)))

(defn any-erred?
  [coll]
  (some erred? coll))

(defn collect
  [& coll]
  (let [errors (vec (remove nil? (mapcat get-errors coll)))]
    (when (seq errors)
      {:errors errors})))

(ns cmr.opendap.errors)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Defaults   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-error-code 400)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Error Messages   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Authorization

(def no-permissions "You do not have permissions to access that resource.")
(def token-required "An ECHO token is required to access this resource.")

;; OUS

(def msg-empty-svc-pattern
  (str "The service pattern computed was empty. Is there a service associated "
       "with the given collection? Does the UMM-S record in question have "
       "values for the pattern fields?"))

(def msg-empty-gnl-data-files
  "Was not able to extract a service data file from the granule.")

(def msg-empty-query-string
  "No OPeNDAP query string was generated for the request.")

(def msg-status-code
  "HTTP Error status code: %s")

(def no-matching-service-pattern
  (str "There was a oroblem creating URLs from granule file data: couldn't "
       "match default service pattern %s to service %s."))

(def granule-metadata
  "There was a problem extracting granule metadata.")

(def service-metadata
  "There was a problem extracting service metadata.")

(def variable-metadata
  "There was a problem extracting variable metadata.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility and Error Support Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check
  ""
  [& msgs]
  (remove nil? (map (fn [[check-fn value msg]] (when (check-fn value) msg))
                    msgs)))

;; XXX Add universal function for checking HTTP status code

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

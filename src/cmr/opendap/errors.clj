(ns cmr.opendap.errors)

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
  (str "Was not able to extract a service data file from the granule."))

(def msg-empty-query-string
  (str "No OPeNDAP query string was generated for the request."))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check
  ""
  [& msgs]
  (remove nil? (map (fn [[value msg]] (when-not value msg)) msgs)))

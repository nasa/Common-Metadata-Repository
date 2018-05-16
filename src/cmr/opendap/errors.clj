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

(def msg-status-code
  (str "HTTP Error status code: %s"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
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

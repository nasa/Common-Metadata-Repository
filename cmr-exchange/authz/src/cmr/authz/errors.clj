(ns cmr.authz.errors
  (:require
   [clojure.set :as set]))

(def error-code 403)
(def no-permissions "You do not have permissions to access that resource.")
(def token-required "An ECHO token is required to access this resource.")

(def status-map
  "This is a lookup data structure for how HTTP status/error codes map to CMR
  OPeNDAP errors."
  {error-code #{no-permissions
                token-required}})

(defn any-errors?
  [errors]
  (seq (set/intersection (get status-map error-code)
                         (set (:errors errors)))))

(ns cmr.ous.util.validation
  (:require
   [cmr.ous.results.errors :as errors]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Predicates   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check-latitude
  [[low high]]
  (and (>= low -90)
       (<= high 90)))

(defn check-longitude
  [[low high]]
  (and (>= low -180)
       (<= high 180)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Validators   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn validate
  [data predicate error-msg]
  (if (predicate data)
    data
    {:errors [error-msg]}))

(defn validate-latitude
  [data]
  (validate data check-latitude errors/invalid-lat-params))

(defn validate-longitude
  [data]
  (validate data check-longitude errors/invalid-lon-params))

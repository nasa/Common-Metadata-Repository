(ns cmr.opendap.components.testing.system
  "A component system setup namespace for use in testing."
  (:require
    [cmr.opendap.components.core :as core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initialization   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([]
    (init :testing))
  ([mode]
    ((mode core/init-lookup))))

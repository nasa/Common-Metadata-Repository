(ns cmr.opendap.components.testing.config
  "A component system setup namespace for use in testing."
  (:require
    [cmr.opendap.components.core :as core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Component Initialization   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init
  ([]
    (init :testing-config-only))
  ([mode]
    ((mode core/init-lookup))))

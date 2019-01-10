(ns cmr.opendap.testing.system
  (:require
    [clojusc.system-manager.core :as system-api]
    [clojusc.twig :as logger]
    [cmr.opendap.components.config :as config]
    [cmr.opendap.components.core]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Setup and Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def setup-options {
  :init 'cmr.opendap.components.core/testing
  :throw-errors true})

(defn init
  "This is used to set the options and any other global data.

  This is defined in a function for re-use. For instance, when a REPL is
  reloaded, the options will be lost and need to be re-applied."
  []
  (logger/set-level! '[] :fatal)
  (system-api/setup-manager setup-options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Convenience Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def manager #'system-api/manager)
(def system #'system-api/system)

(defn http-port
  []
  (config/http-port (system)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Test Fixtures   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-system
  "Testing fixture for system and integration tests."
  [test-fn]
  (init)
  (system-api/startup)
  (test-fn)
  (system-api/shutdown))

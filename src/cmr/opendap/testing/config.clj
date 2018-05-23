(ns cmr.opendap.testing.config
  (:require
    [clojusc.dev.system.core :as system-api]
    [clojusc.twig :as logger]
    [cmr.opendap.components.config :as config]
    [cmr.opendap.components.testing.config]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Setup and Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Hide logging as much as possible before the system starts up, which should
;; disable logging entirely for tests.
(logger/set-level! '[] :fatal)

(def system-ns "cmr.opendap.components.testing.config")
(def ^:dynamic *mgr* (atom nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn startup
  []
  (alter-var-root #'*mgr* (constantly (atom (system-api/create-state-manager))))
  (system-api/set-system-ns (:state @*mgr*) system-ns)
  (system-api/startup @*mgr*))

(defn shutdown
  []
  (when *mgr*
    (let [result (system-api/shutdown @*mgr*)]
      (alter-var-root #'*mgr* (constantly (atom nil)))
      result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Convenience Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn system
  []
  (system-api/get-system (:state @*mgr*)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Test Fixtures   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn with-system
  "Testing fixture for simple system tests that only require access to the
  configuration component."
  [test-fn]
  (startup)
  (test-fn)
  (shutdown))

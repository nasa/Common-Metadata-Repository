(ns cmr.opendap.testing.system
  (:require
    [clojusc.dev.system.core :as system-api]
    [clojusc.twig :as logger]
    [cmr.opendap.components.config :as config]
    [cmr.opendap.components.testing]))

;; Hide logging as much as possible before the system starts up, which should
;; disable logging entirely for tests.
(logger/set-level! '[] :fatal)

(def ^:dynamic *mgr* (atom nil))

(defn startup
  []
  (alter-var-root #'*mgr* (constantly (atom (system-api/create-state-manager))))
  (system-api/set-system-ns (:state @*mgr*) "cmr.opendap.components.testing")
  (system-api/startup @*mgr*))

(defn shutdown
  []
  (when *mgr*
    (let [result (system-api/shutdown @*mgr*)]
      (alter-var-root #'*mgr* (constantly (atom nil)))
      result)))

(defn system
  []
  (system-api/get-system (:state @*mgr*)))

(defn http-port
  []
  (config/http-port (system)))

(defn with-system
  "Testing fixture for system and integration tests."
  [test-fn]
  (startup)
  (test-fn)
  (shutdown))

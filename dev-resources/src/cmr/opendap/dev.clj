(ns cmr.opendap.dev
  "CMR OPeNDAP development namespace."
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [clojure.string :as string]
    [clojure.tools.namespace.repl :as repl]
    [clojusc.dev.system.core :as system-api]
    [clojusc.twig :as logger]
    [cmr.opendap.components.caching :as caching]
    [cmr.opendap.components.config :as config]
    [cmr.opendap.components.core]
    [com.stuartsierra.component :as component]
    [org.httpkit.client :as httpc]
    [trifl.java :refer [show-methods]])
  (:import
    (java.net URI)
    (java.nio.file Paths)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! '[cmr.opendap] :debug)

(def ^:dynamic *mgr* nil)

(defn banner
  []
  (println (slurp (io/resource "text/banner.txt")))
  :ok)

(defn mgr-arg
  []
  (if *mgr*
    *mgr*
    (throw (new Exception
                (str "A state manager is not defined; "
                     "have you run (startup)?")))))

(defn system-arg
  []
  (if-let [state (:state *mgr*)]
    (system-api/get-system state)
    (throw (new Exception
                (str "System data structure is not defined; "
                     "have you run (startup)?")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn startup
  []
  (alter-var-root #'*mgr* (constantly (system-api/create-state-manager)))
  (system-api/set-system-ns (:state *mgr*) "cmr.opendap.components.core")
  (system-api/startup *mgr*))

(defn shutdown
  []
  (when *mgr*
    (let [result (system-api/shutdown (mgr-arg))]
      (alter-var-root #'*mgr* (constantly nil))
      result)))

(defn system
  []
  (system-api/get-system (:state (mgr-arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (shutdown)
  (repl/refresh :after 'cmr.opendap.dev/startup))

(def refresh #'repl/refresh)

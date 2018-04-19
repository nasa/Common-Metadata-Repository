(ns hxgm30.event.dev
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [clojure.tools.namespace.repl :as repl]
    [clojusc.dev.system.core :as system-api]
    [clojusc.twig :as logger]
    [com.stuartsierra.component :as component]
    [hxgm30.event.components.config :as config]
    [hxgm30.event.components.core]
    [hxgm30.event.components.pubsub :as pubsub]
    [hxgm30.event.tag :as tag]
    [trifl.java :refer [show-methods]])
  (:import
    (java.net URI)
    (java.nio.file Paths)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Initial Setup & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(logger/set-level! '[hxgm30] :debug)

(def ^:dynamic *mgr* nil)
(def init-component "hxgm30.event.components.core")
(def after-refresh 'hxgm30.event.dev/startup)

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
;;;   Data Support   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status
  []
  (system-api/get-status (:state (mgr-arg))))

(defn system
  []
  (system-api/get-system (:state (mgr-arg))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn startup
  []
  (alter-var-root #'*mgr* (constantly (system-api/create-state-manager)))
  (system-api/set-system-ns (:state *mgr*) init-component)
  (system-api/startup *mgr*)
  (status))

(defn shutdown
  []
  (when *mgr*
    (let [result (system-api/shutdown (mgr-arg))]
      (alter-var-root #'*mgr* (constantly nil))
      result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Reloading Management   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset
  []
  (shutdown)
  (repl/refresh :after after-refresh))

(def refresh #'repl/refresh)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   XXX   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn publish
  ([data]
    (publish tag/generic data))
  ([event-type data]
    (pubsub/publish (system) event-type data)))

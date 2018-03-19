(ns cmr.graph.system.impl.state
  "CMR Graph system management."
  (:require
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Atom   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *state*
  (atom {:status :stopped
         :system nil
         :ns ""}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord StateTracker [])

(defn get-state
  [_this]
  @*state*)

(defn set-state
  [_this new-state]
  (reset! *state* new-state))

(defn get-status
  [this]
  (:status (get-state this)))

(defn set-status
  [this value]
  (set-state this (assoc (get-state this) :status value)))

(defn get-system
  [this]
  (:system (get-state this)))

(defn set-system
  [this value]
  (set-state this (assoc (get-state this) :system value)))

(defn get-system-ns
  [this]
  (:ns (get-state this)))

(defn set-system-ns
  [this an-ns]
  (set-state this (assoc (get-state this) :ns an-ns)))

(def behaviour
  {:get-state get-state
   :set-state set-state
   :get-status get-status
   :set-status set-status
   :get-system get-system
   :set-system set-system
   :get-system-ns get-system-ns
   :set-system-ns set-system-ns})

(defn create-state-tracker
  []
  (->StateTracker))

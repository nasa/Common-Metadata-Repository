(ns cmr.graph.system.core
  "CMR Graph system management API."
  (:require
    [cmr.graph.system.impl.management :as management]
    [cmr.graph.system.impl.state :as state])
  (:import
    (cmr.graph.system.impl.management StateManager)
    (cmr.graph.system.impl.state StateTracker)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   System State API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol StateTrackerAPI
  (get-state [this])
  (set-state [this new-state])
  (get-status [this])
  (set-status [this value])
  (get-system [this])
  (set-system [this value])
  (get-system-ns [this])
  (set-system-ns [this value]))

(extend StateTracker
        StateTrackerAPI
        state/behaviour)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   State Management API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol StateManagementAPI
  (init [this] [this mode])
  (deinit [this])
  (start [this] [this mode])
  (stop [this])
  (restart [this] [this mode])
  (startup [this] [this mode])
  (shutdown [this]))

(extend StateManager
        StateManagementAPI
        management/behaviour)

(def create-state-manager #'management/create-state-manager)

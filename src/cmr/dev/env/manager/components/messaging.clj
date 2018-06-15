(ns cmr.dev.env.manager.components.messaging
  "System component for inter-component communications."
  (:require
    [cmr.dev.env.manager.components.dem.config :as config]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-messenger
  [system]
  (get-in system [:messaging :messenger]))

(defn publish
  [system topic content]
  (messaging/publish (get-messenger system)
                     topic
                     content))

(defn subscribe
  [system topic subscriber-fn]
  (messaging/subscribe (get-messenger system)
                       topic
                       subscriber-fn))

(defn batch-subscribe
  [system subscribers]
  (messaging/batch-subscribe (get-messenger system)
                             subscribers))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Messaging
  [messenger])

(defn start
  [this]
  (log/info "Starting inter-component messaging component ...")
  (let [messaging-type (config/messaging-type this)
        messenger (messaging/new-messenger messaging-type)]
    (log/debug "Got messaging-type:" messaging-type)
    (log/debug "Got messenger:" messenger)
    (log/debug "Started inter-component messaging component.")
    (assoc this :messenger messenger)))

(defn stop
  [this]
  (log/info "Stopping inter-component messaging component ...")
  (log/debug "Stopped inter-component messaging component.")
  (messaging/stop! (:messenger this))
  ;; XXX close all subscribtion channels; but to do this, we'll need to
  ;;     track them all ...
  (assoc this :messenger nil))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Messaging
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  ""
  []
  (map->Messaging {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX TBD

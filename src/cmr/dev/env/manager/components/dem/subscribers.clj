(ns cmr.dev.env.manager.components.dem.subscribers
  "System component for setting up default inter-component messaging
  subscriptions."
  (:require
    [cmr.dev.env.manager.components.dem.messaging :as messaging-component]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn batch-subscribe
  [system subscribers]
  (messaging/batch-subscribe
   (messaging-component/get-messenger system)
   subscribers))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DefaultSubscribers
  [subscribers])

(defn start
  [this]
  (log/info "Starting default-subscribers component ...")
  (batch-subscribe this (:subscribers this))
  (log/debug "Started default-subscribers component.")
  this)

(defn stop
  [this]
  (log/info "Stopping default-subscribers component ...")
  (let [messenger (messaging-component/get-messenger this)]
    (log/debug "Stopped default-subscribers component.")
    (assoc this :subscribers nil)))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend DefaultSubscribers
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  "The passed argument `subscribers` is a list of maps with each map having
  `:topic` and `:fn` keys with corresponding values."
  [subscribers]
  (map->DefaultSubscribers {:subscribers subscribers}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX TBD

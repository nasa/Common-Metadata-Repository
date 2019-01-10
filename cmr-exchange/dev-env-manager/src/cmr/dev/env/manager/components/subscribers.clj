(ns cmr.dev.env.manager.components.subscribers
  "System component for setting up default inter-component messaging
  subscriptions."
  (:require
    [cmr.dev.env.manager.components.messaging :as messaging-component]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord DefaultSubscribers
  [subscribers])

(defn start
  [this]
  (log/info "Starting default-subscribers component ...")
  (messaging/batch-subscribe this (:subscribers this))
  (log/debug "Started default-subscribers component.")
  this)

(defn stop
  [this]
  (log/info "Stopping default-subscribers component ...")
  (log/debug "Stopped default-subscribers component.")
  (assoc this :subscribers nil))

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

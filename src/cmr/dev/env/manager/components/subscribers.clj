(ns cmr.dev.env.manager.components.subscribers
  "System component for setting up default inter-component messaging
  subscriptions."
  (:require
    [cmr.dev.env.manager.components.messaging :as messaging-component]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

(defn batch-subscribe
  [system subscribers]
  (messaging/batch-subscribe
   (messaging-component/get-messenger system)
   subscribers))

(defrecord DefaultSubscribers [
  subscribers]
  component/Lifecycle

  (start [component]
    (log/info "Starting default-subscribers component ...")
    (batch-subscribe component (:subscribers component))
    (log/debug "Started default-subscribers component.")
    component)

  (stop [component]
    (log/info "Stopping default-subscribers component ...")
    (let [messenger (messaging-component/get-messenger component)]
      (log/debug "Stopped default-subscribers component.")
      (assoc component :subscribers nil))))

(defn create-component
  "The passed argument `subscribers` is a list of maps with each map having
  `:topic` and `:fn` keys with corresponding values."
  [subscribers]
  (map->DefaultSubscribers {:subscribers subscribers}))

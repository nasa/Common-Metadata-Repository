(ns cmr.dev.env.manager.components.messaging
  "System component for inter-component communications."
  (:require
    [cmr.dev.env.manager.config :as config]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

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

(defrecord Messaging [
  messenger]
  component/Lifecycle

  (start [component]
    (log/info "Starting inter-component messaging component ...")
    (let [messaging-type (config/messaging-type component)
          messenger (messaging/new-messenger messaging-type)]
      (log/debug "Got messaging-type:" messaging-type)
      (log/debug "Got messenger:" messenger)
      (log/debug "Started inter-component messaging component.")
      (assoc component :messenger messenger)))

  (stop [component]
    (log/info "Stopping inter-component messaging component ...")
    (log/debug "Stopped inter-component messaging component.")
    (messaging/stop! (:messenger component))
    ;; XXX close all subscribtion channels; but to do this, we'll need to
    ;;     track them all ...
    (assoc component :messenger nil)))

(defn create-component
  ""
  []
  (map->Messaging {}))

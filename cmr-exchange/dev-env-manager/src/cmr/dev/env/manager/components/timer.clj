(ns cmr.dev.env.manager.components..timer
  "System component for setting up a timing component."
  (:require
    [clojure.core.async :as async]
    [cmr.dev.env.manager.components.config :as config]
    [cmr.dev.env.manager.components.messaging :as messaging-component]
    [cmr.dev.env.manager.components.subscribers :as subscribers]
    [cmr.dev.env.manager.messaging.core :as messaging]
    [cmr.dev.env.manager.timing :as timing]
    [com.stuartsierra.component :as component]
    [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- send-message
  [system _times _tracker time-key]
  (messaging-component/publish system :timer {:interval time-key}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Process Component API   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn batch-subscribe
  "The passed argument `subscribers` is a list of maps with each map having
  `:interval` and `:fn` keys with corresponding values."
  [system subscribers]
  (let [messenger (messaging-component/get-messenger system)]
    (log/trace "Timer batch-subscribe using messenger" messenger)
    (doseq [subscriber subscribers]
      (log/debugf "Subscribing %s to %s ..." (:fn subscriber) (:interval subscriber))
      (messaging/subscribe messenger :timer (:fn subscriber)))))

(defn timer
  [system loop-delay update-fn]
  (let [init-tracker (timing/new-tracker timing/default-intervals)]
    (async/go-loop [tracker init-tracker]
      (async/<! (async/timeout loop-delay))
      (recur (timing/update-tracker timing/default-intervals
                                    tracker
                                    update-fn)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Lifecycle Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Timer
  [builder
   loop-interval
   timer-subscribers])

(defn start
  [this]
  (log/info "Starting timer component ...")
  (let [cfg ((:builder this) :timer)
        this (assoc this :config cfg)]
    (timer this
           (config/timer-delay this)
           (partial send-message this))
    (batch-subscribe this (:timer-subscribers this))
    (log/debug "Started timer component.")
    this))

(defn stop
  [this]
  (log/info "Stopping timer component ...")
  (let [messenger (messaging-component/get-messenger this)]
    (log/debug "Stopped timer component.")
    (assoc this :timer-subscribers nil)))

(def lifecycle-behaviour
  {:start start
   :stop stop})

(extend Timer
  component/Lifecycle
  lifecycle-behaviour)

(defn create-component
  "The passed argument `subscribers` is a list of maps with each map having
  `:interval` and `:fn` keys with corresponding values."
  [config-builder-fn loop-interval subscribers]
  (map->Timer
    {:builder config-builder-fn
     :loop-interval loop-interval
     :timer-subscribers subscribers}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Health-check Implementation   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX TBD

(ns cmr.index-queue.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.index-queue.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.system-trace.context :as context]
            [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [cmr.index-queue.services.callback_service :as cs]))

(def DEFAULT_PORT 3010)

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :web :queue])

(defn create-system
  "Returns a new instance of the whole application."
  []
  {:log (log/create-logger)
   :web (web/create-web-server DEFAULT_PORT routes/make-api)
   :zipkin (context/zipkin-config "index-queue" false)
   :queue (cs/create-message-consumer cs/queue-channel-count)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "index-queue System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               this
                               component-order)]
    (info "index-queue System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "index-queue System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))]
    (info "index-queue System stopped")
    stopped-system))

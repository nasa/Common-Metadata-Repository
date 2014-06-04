(ns cmr.ingest.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.api.web-server :as web]
            [cmr.system-trace.context :as context]
            [cmr.ingest.api.routes :as routes]
            [cmr.transmit.config :as transmit-config]))

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :web])


(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :web (web/create-web-server (transmit-config/ingest-port) routes/make-api)
             :zipkin (context/zipkin-config "Ingest" false)}]
    (transmit-config/system-with-connections sys [:metadata-db :indexer])))

   (defn start
     "Performs side effects to initialize the system, acquire resources,
     and start it running. Returns an updated instance of the system."
     [this]
     (info "System starting")
     (let [started-system (reduce (fn [system component-name]
                                    (update-in system [component-name]
                                               #(lifecycle/start % system)))
                                  this
                                  component-order)]
       (info "System started")
       started-system))

   (defn stop
     "Performs side effects to shut down the system and release its
     resources. Returns an updated instance of the system."
     [this]
     (info "System shutting down")
     (let [stopped-system (reduce (fn [system component-name]
                                    (update-in system [component-name]
                                               #(lifecycle/stop % system)))
                                  this
                                  (reverse component-order))]
       (info "System stopped")
       stopped-system))

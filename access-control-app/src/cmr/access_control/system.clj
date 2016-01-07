(ns cmr.access-control.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.transmit.config :as transmit-config]
            [cmr.access-control.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig access-control-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig access-control-public-protocol
  "The protocol to use in documentation examples for the access-control application."
  {:default "http"})

(def public-conf
  "Public access-control configuration used for generating example requests in documentation"
  {:protocol (access-control-public-protocol)
   :relative-root-url (transmit-config/access-control-relative-root-url)})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  {:log (log/create-logger)
   :web (web/create-web-server (transmit-config/access-control-port) routes/make-api)
   :nrepl (nrepl/create-nrepl-if-configured (access-control-nrepl-port))
   :public-conf public-conf
   :relative-root-url (transmit-config/access-control-relative-root-url)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "access-control System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               this
                               component-order)]
    (info "access-control System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "access-control System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))]
    (info "access-control System stopped")
    stopped-system))

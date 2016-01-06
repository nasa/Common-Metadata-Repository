(ns cmr.access-control.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.access-control.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.common.config :as cfg :refer [defconfig]]))

(defconfig access-control-port
  "Port access-control application listens on."
  {:default 3011 :type Long})

(defconfig access-control-relative-root-url
  "Defines a root path that will appear on all requests sent to this application. For
  example if the relative-root-url is '/cmr-app' and the path for a URL is '/foo' then
  the full url would be http://host:port/cmr-app/foo. This should be set when this
  application is deployed in an environment where it is accessed through a VIP."
  {:default ""})

(defconfig access-control-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  {:log (log/create-logger)
   :web (web/create-web-server (access-control-port) routes/make-api)
   :nrepl (nrepl/create-nrepl-if-configured (access-control-nrepl-port))
   :relative-root-url (access-control-relative-root-url)})

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

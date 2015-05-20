(ns cmr.index-set.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.index-set.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.system-trace.context :as context]
            [cmr.index-set.data.elasticsearch :as es]
            [cmr.elastic-utils.config :as es-config]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.acl.core :as acl]))

(defconfig index-set-port
  "Port index-set application listens on."
  {:default 3005 :type Long})

(defconfig index-set-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :index :web :nrepl])

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :index (es/create-elasticsearch-store (es-config/elastic-config))
             :web (web/create-web-server (index-set-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (index-set-nrepl-port))
             :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)}
             :zipkin (context/zipkin-config "index-set" false)
             :relative-root-url (transmit-config/index-set-relative-root-url)}]
    (transmit-config/system-with-connections sys [:echo-rest])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "index-set System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               this
                               component-order)]
    (info "index-set System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "index-set System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))]
    (info "index-set System stopped")
    stopped-system))

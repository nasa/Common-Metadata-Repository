(ns cmr.cubby.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.cubby.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.system-trace.context :as context]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.elastic-utils.config :as es-config]
            [cmr.acl.core :as acl]
            [cmr.cubby.data.elastic-cache-store :as elastic-cache-store]
            [cmr.transmit.config :as transmit-config]))

(defconfig cubby-port
  "Port cubby application listens on."
  {:default 3007 :type Long})

(defconfig cubby-relative-root-url
  "Defines a root path that will appear on all requests sent to this application. For
  example if the relative-root-url is '/cmr-app' and the path for a URL is '/foo' then
  the full url would be http://host:port/cmr-app/foo. This should be set when this
  application is deployed in an environment where it is accessed through a VIP."
  {:default ""})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :db :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :db (elastic-cache-store/create-elastic-cache-store (es-config/elastic-config))
             :web (web/create-web-server (cubby-port) routes/make-api)
             :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)}
             :relative-root-url (cubby-relative-root-url)
             :zipkin (context/zipkin-config "cubby" false)}]
    (transmit-config/system-with-connections sys [:echo-rest])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "cubby System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               this
                               component-order)]
    (info "cubby System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "cubby System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))]
    (info "cubby System stopped")
    stopped-system))

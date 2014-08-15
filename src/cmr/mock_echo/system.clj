(ns cmr.mock-echo.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.mock-echo.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.mock-echo.data.token-db :as token-db]
            [cmr.mock-echo.data.provider-db :as provider-db]
            [cmr.mock-echo.data.acl-db :as acl-db]
            [cmr.system-trace.context :as context]))

(def DEFAULT_PORT 3007)

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  {:log (log/create-logger)
   :token-db (token-db/create-db)
   :provider-db (provider-db/create-db)
   :acl-db (acl-db/create-db)
   :web (web/create-web-server DEFAULT_PORT routes/make-api)
   :zipkin (context/zipkin-config "mock-echo" false)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "mock-echo System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/start % system)))
                               this
                               component-order)]
    (info "mock-echo System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "mock-echo System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/stop % system)))
                               this
                               (reverse component-order))]
    (info "mock-echo System stopped")
    stopped-system))

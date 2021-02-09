(ns cmr.mock-echo.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.common.api.context :as context]
   [cmr.common.api.web-server :as web]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.system :as common-sys]
   [cmr.mock-echo.api.routes :as routes]
   [cmr.mock-echo.data.acl-db :as acl-db]
   [cmr.mock-echo.data.provider-db :as provider-db]
   [cmr.mock-echo.data.token-db :as token-db]
   [cmr.mock-echo.data.urs-db :as urs-db]
   [cmr.transmit.config :as transmit-config]))

(def ^:private component-order
  "Defines the order to start the components."
  [:log :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [system {:log (log/create-logger)
                :token-db (token-db/create-db)
                :provider-db (provider-db/create-db)
                :acl-db (acl-db/create-db)
                :urs-db (urs-db/create-db)
                :web (web/create-web-server (transmit-config/mock-echo-port) routes/make-api)
                :relative-root-url (transmit-config/mock-echo-relative-root-url)}]
    (transmit-config/system-with-connections system [:access-control])))

(def start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  (common-sys/start-fn "mock-echo" component-order))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "mock-echo" component-order))

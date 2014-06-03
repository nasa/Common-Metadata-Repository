(ns cmr.bootstrap.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.bootstrap.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.oracle.connection :as oracle]
            [cmr.system-trace.context :as context]
            [clojure.core.async :as ca :refer [chan]]
            [cmr.bootstrap.data.bulk-migration :as bm]
            [cmr.oracle.config :as oracle-config]))

(def DEFAULT_PORT 3006)

(def CHANNEL_BUFFER_SIZE 10)

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :db :web])

(defn create-system
  "Returns a new instance of the whole application."
  []
  {:log (log/create-logger)
   ;; Channel for requesting full provider migration - provider/collections/granules.
   ;; Takes single provider-id strings.
   :provider-channel (chan CHANNEL_BUFFER_SIZE)
   ;; Channel for requesting single collection/granules migration.
   ;; Takes maps, e.g., {:collection-id collection-id :provider-id provider-id}
   :collection-channel (chan CHANNEL_BUFFER_SIZE)
   :db (oracle/create-db (apply oracle/db-spec (oracle-config/db-spec-args)))
   :web (web/create-web-server DEFAULT_PORT routes/make-api)
   :zipkin (context/zipkin-config "bootstrap" false)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "bootstrap System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/start % system)))
                               this
                               component-order)]
    (oracle/test-db-connection! (:db started-system))
    (bm/handle-copy-requests this)
    (info "Bootstrap System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "bootstrap System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(lifecycle/stop % system)))
                               this
                               (reverse component-order))]
    (info "bootstrap System stopped")
    stopped-system))

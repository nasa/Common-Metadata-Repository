(ns cmr.cubby.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.cubby.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.elastic-utils.config :as es-config]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
            [cmr.cubby.data.elastic-cache-store :as elastic-cache-store]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.system :as common-sys]))

(defconfig cubby-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :db :web :nrepl])

(defn create-elastic
  []
  (let [http-port (es-config/elastic-port)]
    (elastic-server/create-server http-port
                                  (+ http-port 10)
                                  "es_data/dev_system")))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :db (elastic-cache-store/create-elastic-cache-store (es-config/elastic-config))
            ;  :elastic-server (create-elastic)
             :web (web/create-web-server (transmit-config/cubby-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (cubby-nrepl-port))
             :relative-root-url (transmit-config/cubby-relative-root-url)}]
    (transmit-config/system-with-connections sys [:echo-rest])))

(def start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  (common-sys/start-fn "cubby" component-order))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "cubby" component-order))

(defn dev-start
  "Starts the system but performs extra calls to make sure all indexes are created in elastic."
  [system]
  (let [started-system (start system)]
    (elastic-cache-store/create-index-or-update-mappings (:db started-system))
    started-system))

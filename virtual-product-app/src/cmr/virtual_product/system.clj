(ns cmr.virtual-product.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common.api.web-server :as web]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer [debug info warn error]]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.message-queue.config :as queue-config]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.transmit.config :as transmit-config]
   [cmr.virtual-product.api.routes :as routes]
   [cmr.virtual-product.config :as config]
   [cmr.virtual-product.services.virtual-product-service :as vps]))

(defconfig virtual-product-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig log-level
  "App logging level"
  {:default "info"})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger-with-log-level (log-level))
             :web (web/create-web-server (transmit-config/virtual-product-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (virtual-product-nrepl-port))
             :relative-root-url (transmit-config/virtual-product-relative-root-url)
             :queue-broker (queue-broker/create-queue-broker (config/queue-config))
             :caches {common-health/health-cache-key (common-health/create-health-cache)}
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [jvm-info/log-jvm-statistics-job])}]
    (transmit-config/system-with-connections sys [:metadata-db :ingest :search])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "virtual-product System starting")
  (let [started-system (common-sys/start this component-order)]

    (vps/subscribe-to-ingest-events {:system started-system})

    (info "virtual-product System started")
    started-system))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "virtual product" component-order))

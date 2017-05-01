(ns cmr.access-control.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.access-control.config :as config]
   [cmr.access-control.data.access-control-index :as access-control-index]
   [cmr.access-control.data.group-fetcher :as gf]
   [cmr.access-control.services.event-handler :as event-handler]
   [cmr.access-control.test.bootstrap :as bootstrap]
   [cmr.access-control.routes :as routes]
   [cmr.acl.acl-fetcher :as af]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.search.elastic-search-index :as search-index]
   [cmr.common.api.web-server :as web-server]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.message-queue.config :as queue-config]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.transmit.config :as transmit-config]))

(defconfig access-control-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig access-control-public-protocol
  "The protocol to use in documentation examples for the access-control application."
  {:default "http"})

(defconfig access-control-public-host
  "The host name to use in links returned by the access-control application."
  {:default "localhost"})

(defconfig access-control-public-port
  "The port to use in links returned by the access-control application."
  {:default 3011
   :type Long})

(defconfig log-level
  "App logging level"
  {:default "info"})

(defn public-conf
  "Public access-control configuration used for generating example requests in documentation"
  []
  {:protocol (access-control-public-protocol)
   :host (access-control-public-host)
   :port (access-control-public-port)
   :relative-root-url (transmit-config/access-control-relative-root-url)})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :search-index :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger-with-log-level (log-level))
             :search-index (search-index/create-elastic-search-index)
             :web (web-server/create-web-server (transmit-config/access-control-port) routes/handlers)
             :nrepl (nrepl/create-nrepl-if-configured (access-control-nrepl-port))
             :queue-broker (queue-broker/create-queue-broker (config/queue-config))
             :caches {af/acl-cache-key (af/create-acl-cache
                                        [:system-object :provider-object :single-instance-object])
                      gf/group-cache-key (gf/create-cache)
                      common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                      common-health/health-cache-key (common-health/create-health-cache)}

             :public-conf (public-conf)
             :relative-root-url (transmit-config/access-control-relative-root-url)
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "access-control-acl-cache-refresh")
                          jvm-info/log-jvm-statistics-job])}]
    (transmit-config/system-with-connections sys [:echo-rest :metadata-db :urs :cubby])))

(defn start
  "Performs side effects to initialize the system, acquire resources, and start it running. Returns
  an updated instance of the system."
  [system]
  (info "Access Control system starting")
  (let [started-system (common-sys/start system component-order)]
    (when (:queue-broker system)
      (event-handler/subscribe-to-events {:system started-system}))

    (info "Access control system started")
    started-system))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "access-control" component-order))

(defn dev-start
  "Starts the system but performs extra calls to make sure all indexes are created in elastic and
   bootstrap necessary local test data."
  [system]
  (let [started-system (start system)]
    (try
      (access-control-index/create-index-or-update-mappings (:search-index started-system))
      (bootstrap/bootstrap started-system)
      (catch Exception e
        (common-sys/stop started-system component-order)
        (throw e)))
    started-system))

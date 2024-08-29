(ns cmr.access-control.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.access-control.config :as config]
   [cmr.elastic-utils.search.access-control-index :as access-control-index]
   [cmr.access-control.services.event-handler :as event-handler]
   [cmr.access-control.test.bootstrap :as bootstrap]
   [cmr.access-control.routes :as routes]
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.cache-info :as cache-info]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.provider-cache :as provider-cache]
   [cmr.common.api.web-server :as web-server]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log :refer [info]]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.elastic-utils.search.es-index :as search-index]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]
   [cmr.transmit.urs :as urs]))

(declare access-control-nrepl-port)
(defconfig access-control-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(declare log-level)
(defconfig log-level
  "App logging level"
  {:default "info"})

(declare access-control-public-protocol)
(defconfig access-control-public-protocol
  "The protocol to use for public access to the access-control application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "http"})

(declare access-control-public-host)
(defconfig access-control-public-host
  "The host name to use for public access to the access-control application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "localhost"})

(declare set-access-control-public-port!)
(declare access-control-public-port)
(defconfig access-control-public-port
  "The port to use for public access to the access-control application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default 3011
   :type Long})

(defn public-conf
  "Public access-control configuration used for generating proper link URLs in
  dynamic content (templates), generating example requests in documentation,
  and running the access-control service in the development environment for use
  with integration tests."
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

(def hours->ms "Convert hours to ms" (partial * 1000 3600))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:instance-name (common-sys/instance-name "access-control")
             :log (log/create-logger-with-log-level (log-level))
             :search-index (search-index/create-elastic-search-index)
             :web (web-server/create-web-server (transmit-config/access-control-port) routes/handlers)
             :nrepl (nrepl/create-nrepl-if-configured (access-control-nrepl-port))
             :queue-broker (queue-broker/create-queue-broker (config/queue-config))
             :caches {af/acl-cache-key (af/create-acl-cache
                                        [:system-object :provider-object :single-instance-object])
                      provider-cache/cache-key (provider-cache/create-cache)
                      acl/collection-field-constraints-cache-key (acl/create-access-constraints-cache)
                      common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                      common-health/health-cache-key (common-health/create-health-cache)
                      launchpad-user-cache/launchpad-user-cache-key (launchpad-user-cache/create-launchpad-user-cache)
                      urs/urs-cache-key (urs/create-urs-cache)}

             :public-conf (public-conf)
             :relative-root-url (transmit-config/access-control-relative-root-url)
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "access-control-acl-cache-refresh")
                          jvm-info/log-jvm-statistics-job
                          (cache-info/create-log-cache-info-job "access-control")])}]
    (transmit-config/system-with-connections sys [:access-control :echo-rest :metadata-db :urs])))

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
      ;; This is needed to bootstrap admin group for legacy services for integration tests
      (bootstrap/bootstrap started-system)
      (catch Exception e
        (common-sys/stop started-system component-order)
        (throw e)))
    started-system))

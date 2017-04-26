(ns cmr.ingest.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.cache.consistent-cache :as consistent-cache]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.api.web-server :as web]
   [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.ingest.api.ingest :as ingest-api]
   [cmr.ingest.api.routes :as routes]
   [cmr.ingest.config :as config]
   [cmr.ingest.services.event-handler :as event-handler]
   [cmr.ingest.services.humanizer-alias-cache :as humanizer-alias-cache]
   [cmr.ingest.services.jobs :as ingest-jobs]
   [cmr.ingest.services.providers-cache :as pc]
   [cmr.message-queue.config :as queue-config]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.oracle.config :as oracle-config]
   [cmr.oracle.connection :as oracle]
   [cmr.transmit.config :as transmit-config]))

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :db :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defconfig ingest-public-protocol
  "The protocol to use in documentation examples for the ingest application."
  {:default "http"})

(defconfig log-level
  "App logging level"
  {:default "info"})

(def ingest-public-conf
  "Public ingest configuration used for generating example requests in documentation"
  {:protocol (ingest-public-protocol)
   :relative-root-url (transmit-config/ingest-relative-root-url)})

(defn create-system
  "Returns a new instance of the whole application."
  ([]
   (create-system "ingest"))
  ([connection-pool-name]
   (let [sys {:log (log/create-logger-with-log-level (log-level))
              :web (web/create-web-server (transmit-config/ingest-port) routes/make-api)
              :nrepl (nrepl/create-nrepl-if-configured (config/ingest-nrepl-port))
              :db (oracle/create-db (config/db-spec connection-pool-name))
              :scheduler (jobs/create-clustered-scheduler
                          `system-holder :db
                          (conj (ingest-jobs/jobs)
                                (af/refresh-acl-cache-job "ingest-acl-cache-refresh")
                                jvm-info/log-jvm-statistics-job))
              :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)
                       pc/providers-cache-key (pc/create-providers-cache)
                       af/acl-cache-key (af/create-consistent-acl-cache
                                         [:catalog-item :system-object :provider-object])
                       ingest-api/user-id-cache-key (ingest-api/create-user-id-cache)
                       kf/kms-cache-key (kf/create-kms-cache)
                       common-health/health-cache-key (common-health/create-health-cache)
                       common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                       humanizer-alias-cache/humanizer-alias-cache-key (humanizer-alias-cache/create-cache)}
              :ingest-public-conf ingest-public-conf
              :queue-broker (queue-broker/create-queue-broker (config/queue-config))}]
     (transmit-config/system-with-connections
      sys [:metadata-db :indexer :echo-rest :search :cubby :kms]))))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (let [started-system (common-sys/start system component-order)]
    (when (:queue-broker system)
      (event-handler/subscribe-to-events {:system started-system}))
    started-system))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "ingest" component-order))

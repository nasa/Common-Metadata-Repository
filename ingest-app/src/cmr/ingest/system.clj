(ns cmr.ingest.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common-app.data.humanizer-alias-cache :as humanizer-alias-cache]
   [cmr.common-app.services.cache-info :as cache-info]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kl]
   [cmr.common.api.web-server :as web]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.ingest.validation.generic-document-validation :as generic-validation]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.ingest.api.core :as ingest-api]
   [cmr.ingest.api.translation :as ingest-translation]
   [cmr.ingest.config :as config]
   [cmr.ingest.routes :as routes]
   [cmr.ingest.services.event-handler :as event-handler]
   [cmr.ingest.services.jobs :as ingest-jobs]
   [cmr.ingest.services.providers-cache :as pc]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]
   [cmr.transmit.urs :as urs]))

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :db :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defconfig log-level
  "App logging level"
  {:default "info"})

(defconfig ingest-public-protocol
  "The protocol to use for public access to the ingest application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "http"})

(defconfig ingest-public-host
  "The host name to use for public access to the ingest application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "localhost"})

(defconfig ingest-public-port
  "The port to use for public access to the ingest application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default 3002
   :type Long})

(defn public-conf
  "Public ingest configuration used for generating proper link URLs in dynamic
  content (templates), generating example requests in documentation, and
  running the ingest service in the development environment for use with
  integration tests."
  []
  {:protocol (ingest-public-protocol)
   :host (ingest-public-host)
   :port (ingest-public-port)
   :relative-root-url (transmit-config/ingest-relative-root-url)})

(defn create-system
  "Returns a new instance of the whole application."
  ([]
   (create-system "ingest"))
  ([connection-pool-name]
   (let [sys {:instance-name (common-sys/instance-name connection-pool-name)
              :log (log/create-logger-with-log-level (log-level))
              :web (web/create-web-server (transmit-config/ingest-port) routes/handlers)
              :nrepl (nrepl/create-nrepl-if-configured (config/ingest-nrepl-port))
              :db (mdb-util/create-db (config/db-spec connection-pool-name))
              :scheduler (jobs/create-clustered-scheduler
                           `system-holder :db
                           (conj (ingest-jobs/jobs)
                                 (af/refresh-acl-cache-job "ingest-acl-cache-refresh")
                                 jvm-info/log-jvm-statistics-job
                                 (cache-info/create-log-cache-info-job "ingest")))
              :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)
                       acl/token-smp-cache-key (acl/create-token-smp-cache)
                       acl/token-pc-cache-key (acl/create-token-pc-cache)
                       ;; Caches a map of tokens to the security identifiers
                       context-augmenter/token-sid-cache-name (context-augmenter/create-token-sid-cache)
                       context-augmenter/token-user-id-cache-name (context-augmenter/create-token-user-id-cache)

                       pc/providers-cache-key (pc/create-providers-cache)
                       af/acl-cache-key (af/create-consistent-acl-cache
                                          [:catalog-item :system-object :provider-object])
                       ingest-api/user-id-cache-key (ingest-api/create-user-id-cache)
                       ingest-translation/xsl-transformer-cache-name (mem-cache/create-in-memory-cache)
                       kf/kms-cache-key (kf/create-kms-cache)
                       kl/kms-short-name-cache-key (kl/create-kms-short-name-cache)
                       kl/kms-umm-c-cache-key (kl/create-kms-umm-c-cache)
                       kl/kms-location-cache-key (kl/create-kms-location-cache)
                       kl/kms-measurement-cache-key (kl/create-kms-measurement-cache)
                       common-health/health-cache-key (common-health/create-health-cache)
                       common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                       humanizer-alias-cache/humanizer-alias-cache-key (humanizer-alias-cache/create-cache-client)
                       launchpad-user-cache/launchpad-user-cache-key (launchpad-user-cache/create-launchpad-user-cache)
                       urs/urs-cache-key (urs/create-urs-cache)
                       generic-validation/schema-validation-cache-key (generic-validation/create-schema-validation-cache)}
              :public-conf (public-conf)
              :queue-broker (queue-broker/create-queue-broker (config/queue-config))}]
     (transmit-config/system-with-connections
       sys [:metadata-db :indexer :access-control :echo-rest :search :kms :ordering :urs]))))

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
  (let [stopped-system (common-sys/stop-fn "ingest" component-order)]
    stopped-system))

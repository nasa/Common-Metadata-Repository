(ns cmr.indexer.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.health :as common-health]
   [cmr.transmit.cache.consistent-cache :as consistent-cache]
   [cmr.common-app.services.cache-info :as cache-info]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.api.web-server :as web]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log :refer [info]]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.api.routes :as routes]
   [cmr.indexer.config :as config]
   [cmr.indexer.data.collection-granule-aggregation-cache :as cgac]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.humanizer-fetcher :as hf]
   [cmr.indexer.data.index-set :as index-set]
   [cmr.indexer.data.metrics-fetcher :as metrics-fetcher]
   [cmr.indexer.services.event-handler :as event-handler]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.transmit.config :as transmit-config]))

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :db :scheduler :queue-broker :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defconfig index-set-cache-consistent-timeout-seconds
  "The number of seconds between when the indexer's index set cache should check with Redis for consistence"
  {:default 5
   :type Long})

(def index-set-mappings-redis-keys
  "The list of keys related to index-set-mappings which should be deleted when we want to clear
  the index-set-mappings cache."
  [":concept-mapping-types-hash-code" ":concept-indices-hash-code"])

(defconfig log-level
  "App logging level"
  {:default "info"})

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger-with-log-level (log-level))
             :db (es/create-elasticsearch-store (es-config/elastic-config))
             :web (web/create-web-server (transmit-config/indexer-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (config/indexer-nrepl-port))
             :relative-root-url (transmit-config/indexer-relative-root-url)
             :caches {af/acl-cache-key (af/create-consistent-acl-cache
                                        [:catalog-item :system-object :provider-object])
                      index-set/index-set-cache-key (consistent-cache/create-consistent-cache
                                                     {:hash-timeout-seconds (index-set-cache-consistent-timeout-seconds)
                                                      :keys-to-track index-set-mappings-redis-keys})
                      acl/token-imp-cache-key (acl/create-token-imp-cache)
                      kf/kms-cache-key (kf/create-kms-cache)
                      cgac/coll-gran-aggregate-cache-key (cgac/create-cache)
                      hf/humanizer-cache-key (hf/create-cache)
                      metrics-fetcher/usage-metrics-cache-key (metrics-fetcher/create-cache)
                      common-health/health-cache-key (common-health/create-health-cache)}
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "indexer-acl-cache-refresh")
                          (kf/refresh-kms-cache-job "indexer-kms-cache-refresh")
                          jvm-info/log-jvm-statistics-job
                          (cache-info/create-log-cache-info-job "indexer")])
             :queue-broker (queue-broker/create-queue-broker (config/queue-config))}]

    (transmit-config/system-with-connections sys [:metadata-db :access-control :echo-rest :kms :search])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (info "Indexer system starting")
  (let [started-system (common-sys/start system component-order)]

    (when (:queue-broker system)
      (event-handler/subscribe-to-events {:system started-system}))

    (info "Indexer system started")
    started-system))

(defn dev-start
  "Starts the system but performs extra calls to make sure all indexes are created in elastic."
  [system]
  (let [started-system (start system)
        context {:system started-system}]
    ;; The indexes/alias will not be created if they already exist.
    (try
      (es/create-indexes context)
      (when (es/requires-update? context)
        (es/update-indexes context {}))
      (catch Exception e
        (common-sys/stop started-system component-order)
        (throw e)))
    started-system))

(def stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  (common-sys/stop-fn "indexer" component-order))

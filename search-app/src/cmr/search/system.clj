(ns cmr.search.system
  (:require
   [clojure.core.cache :as clj-cache]
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.collection-renderer.services.collection-renderer :as collection-renderer]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.search :as search]
   [cmr.common-app.services.search.elastic-search-index :as common-idx]
   [cmr.common.api.web-server :as web-server]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.metadata-db.system :as mdb-system]
   [cmr.orbits.orbits-runtime :as orbits-runtime]
   [cmr.search.api.request-context-user-augmenter :as context-augmenter]
   [cmr.search.data.elastic-search-index :as idx]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.models.query :as q]
   [cmr.search.routes :as routes]
   [cmr.search.services.acls.collections-cache :as coll-cache]
   [cmr.search.services.humanizers.humanizer-report-service :as hrs]
   [cmr.search.services.query-execution.has-granules-results-feature :as hgrf]
   [cmr.transmit.config :as transmit-config]))

;; Design based on http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts

(defconfig search-public-protocol
  "The protocol to use for public access to the search application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "http"})

(defconfig search-public-host
  "The host name to use for public access to the search application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default "localhost"})

(defconfig search-public-port
  "The port to use for public access to the search application.

  Note: this configuration value is used as-is in local, dev environments
  and is overridden with ENV variables in remote deployments. In both cases,
  this configuration information is utilized and required. See `defconfig` for
  more details."
  {:default 3003
   :type Long})

(defconfig search-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(defconfig log-level
  "App logging level"
  {:default "info"})

(defn public-conf
  "Public search configuration used for generating proper link URLs in dynamic
  content (templates), generating example requests in documentation, and
  running the search service in the development environment for use with
  integration tests."
  []
  {:protocol (search-public-protocol)
   :host (search-public-host)
   :port (search-public-port)
   :relative-root-url (transmit-config/search-relative-root-url)})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches collection-renderer/system-key orbits-runtime/system-key :search-index :scheduler
   :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [metadata-db (-> (mdb-system/create-system "metadata-db-in-search-app-pool")
                        (dissoc :log :web :scheduler :unclustered-scheduler))
        sys {:log (log/create-logger-with-log-level (log-level))
             ;; An embedded version of the metadata db app to allow quick retrieval of data
             ;; from oracle.
             :embedded-systems {:metadata-db metadata-db}
             :search-index (common-idx/create-elastic-search-index)
             :web (web-server/create-web-server (transmit-config/search-port) routes/handlers)
             :nrepl (nrepl/create-nrepl-if-configured (search-nrepl-port))
             ;; Caches added to this list must be explicitly cleared in query-service/clear-cache
             :caches {idx/index-cache-name (mem-cache/create-in-memory-cache)
                      af/acl-cache-key (af/create-acl-cache [:catalog-item :system-object])
                      ;; Caches a map of tokens to the security identifiers
                      context-augmenter/token-sid-cache-name (context-augmenter/create-token-sid-cache)
                      context-augmenter/token-user-id-cache-name (context-augmenter/create-token-user-id-cache)
                      :has-granules-map (hgrf/create-has-granules-map-cache)
                      coll-cache/cache-key (coll-cache/create-cache)
                      metadata-transformer/xsl-transformer-cache-name (mem-cache/create-in-memory-cache)
                      acl/token-imp-cache-key (acl/create-token-imp-cache)
                      ;; Note that search does not have a job to refresh the KMS cache. The indexer
                      ;; already refreshes the cache. Since we use a consistent cache, the search
                      ;; application will also pick up the updated KMS keywords.
                      kf/kms-cache-key (kf/create-kms-cache)
                      search/scroll-id-cache-key (search/create-scroll-id-cache)
                      metadata-cache/cache-key (metadata-cache/create-cache)
                      common-health/health-cache-key (common-health/create-health-cache)
                      common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                      hrs/report-cache-key (hrs/create-report-cache)}
             :public-conf (public-conf)
             collection-renderer/system-key (collection-renderer/create-collection-renderer)
             orbits-runtime/system-key (orbits-runtime/create-orbits-runtime)
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "search-acl-cache-refresh")
                          idx/refresh-index-names-cache-job
                          hgrf/refresh-has-granules-map-job
                          (metadata-cache/refresh-collections-metadata-cache-job)
                          coll-cache/refresh-collections-cache-for-granule-acls-job
                          jvm-info/log-jvm-statistics-job
                          hrs/humanizer-report-generator-job])}]
    (transmit-config/system-with-connections
      sys
      [:index-set :echo-rest :metadata-db :kms :cubby :access-control])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")
  (let [started-system (-> this
                           (update-in [:embedded-systems :metadata-db] mdb-system/start)
                           (common-sys/start component-order))]
    (info "System started")
    started-system))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")
  (let [stopped-system (-> this
                           (common-sys/stop component-order)
                           (update-in [:embedded-systems :metadata-db] mdb-system/stop))]
    (info "System stopped")
    stopped-system))

(ns cmr.search.system
  (:require
   [cmr.acl.acl-fetcher :as af]
   [cmr.acl.core :as acl]
   [cmr.common-app.api.enabled :as common-enabled]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.api.request-context-user-augmenter :as context-augmenter]
   [cmr.common-app.data.collections-for-gran-acls-by-concept-id-cache :as coll-gran-acls-caches]
   [cmr.common-app.data.metadata-retrieval.collection-metadata-cache :as cmn-coll-metadata-cache]
   [cmr.common-app.services.cache-info :as cache-info]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common-app.services.kms-lookup :as kl]
   [cmr.common-app.services.search :as search]
   [cmr.common.api.web-server :as web-server]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.log :as log]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.elastic-utils.search.es-index :as common-idx]
   [cmr.elastic-utils.search.es-index-name-cache :as elastic-search-index-names-cache]
   [cmr.metadata-db.system :as mdb-system]
   [cmr.orbits.orbits-runtime :as orbits-runtime]
   [cmr.search.data.granule-counts-cache :as granule-counts-cache]
   [cmr.search.data.metadata-retrieval.metadata-cache :as metadata-cache]
   [cmr.search.data.metadata-retrieval.metadata-transformer :as metadata-transformer]
   [cmr.search.routes :as routes]
   [cmr.search.services.humanizers.humanizer-range-facet-service :as hrfs]
   [cmr.search.services.humanizers.humanizer-report-service :as hrs]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as hgocrf]
   [cmr.search.services.query-execution.has-granules-results-feature :as hgrf]
   [cmr.transmit.config :as transmit-config]
   [cmr.transmit.launchpad-user-cache :as launchpad-user-cache]
   [cmr.transmit.urs :as urs]))

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
  [:log
   :caches
   orbits-runtime/system-key
   :search-index
   :scheduler
   :web
   :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [metadata-db (-> (mdb-system/create-system "metadata-db-in-search-app-pool")
                        (dissoc :log :web :scheduler :unclustered-scheduler))
        sys {:instance-name (common-sys/instance-name "search")
             :log (log/create-logger-with-log-level (log-level))
             ;; An embedded version of the metadata db app to allow quick retrieval of data
             ;; from oracle.
             :embedded-systems {:metadata-db metadata-db}
             :search-index (common-idx/create-elastic-search-index)
             :web (web-server/create-web-server (transmit-config/search-port) routes/handlers)
             :nrepl (nrepl/create-nrepl-if-configured (search-nrepl-port))
             ;; Caches added to this list must be explicitly cleared in query-service/clear-cache
             :caches {elastic-search-index-names-cache/index-names-cache-key (elastic-search-index-names-cache/create-index-cache)
                      af/acl-cache-key (af/create-acl-cache [:catalog-item :system-object :provider-object])
                      ;; Caches a map of tokens to the security identifiers
                      context-augmenter/token-sid-cache-name (context-augmenter/create-token-sid-cache)
                      context-augmenter/token-user-id-cache-name (context-augmenter/create-token-user-id-cache)
                      :has-granules-map (hgrf/create-has-granules-map-cache)
                      hgocrf/has-granules-or-cwic-cache-key (hgocrf/create-has-granules-or-cwic-map-cache)
                      :has-granules-or-opensearch-map (hgocrf/create-has-granules-or-opensearch-map-cache)
                      metadata-transformer/xsl-transformer-cache-name (mem-cache/create-in-memory-cache)
                      acl/token-imp-cache-key (acl/create-token-imp-cache)
                      acl/token-pc-cache-key (acl/create-token-pc-cache)
                      launchpad-user-cache/launchpad-user-cache-key (launchpad-user-cache/create-launchpad-user-cache)
                      urs/urs-cache-key (urs/create-urs-cache)
                      kf/kms-cache-key (kf/create-kms-cache)
                      kl/kms-short-name-cache-key (kl/create-kms-short-name-cache)
                      kl/kms-umm-c-cache-key (kl/create-kms-umm-c-cache)
                      kl/kms-location-cache-key (kl/create-kms-location-cache)
                      kl/kms-measurement-cache-key (kl/create-kms-measurement-cache)
                      search/scroll-id-cache-key (search/create-scroll-id-cache)
                      search/scroll-first-page-cache-key (search/create-scroll-first-page-cache)
                      cmn-coll-metadata-cache/cache-key (cmn-coll-metadata-cache/create-cache)
                      coll-gran-acls-caches/coll-by-concept-id-cache-key (coll-gran-acls-caches/create-coll-by-concept-id-cache-client)
                      common-health/health-cache-key (common-health/create-health-cache)
                      common-enabled/write-enabled-cache-key (common-enabled/create-write-enabled-cache)
                      hrs/humanizer-report-cache-key (hrs/create-humanizer-report-cache-client)
                      hrfs/range-facet-cache-key (hrfs/create-range-facet-cache)
                      granule-counts-cache/granule-counts-cache-key (granule-counts-cache/create-granule-counts-cache-client)}
             :public-conf (public-conf)
             orbits-runtime/system-key (orbits-runtime/create-orbits-runtime)
             ;; Note that some of these jobs only need to run on one node, but we are currently
             ;; running all jobs on all nodes
             :scheduler (jobs/create-scheduler
                         `system-holder
                         [(af/refresh-acl-cache-job "search-acl-cache-refresh")
                          hgrf/refresh-has-granules-map-job
                          hgocrf/refresh-has-granules-or-opensearch-map-job
                          (metadata-cache/refresh-collections-metadata-cache-job)
                          (metadata-cache/update-collections-metadata-cache-job)
                          (cache-info/create-log-cache-info-job "search")
                          jvm-info/log-jvm-statistics-job])}]
    (transmit-config/system-with-connections
     sys
     [:indexer :echo-rest :metadata-db :kms :access-control :urs])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (let [started-system (-> this
                           (update-in [:embedded-systems :metadata-db] mdb-system/start)
                           (common-sys/start component-order))]
    started-system))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (let [stopped-system (-> this
                           (common-sys/stop component-order)
                           (update-in [:embedded-systems :metadata-db] mdb-system/stop))]
    stopped-system))


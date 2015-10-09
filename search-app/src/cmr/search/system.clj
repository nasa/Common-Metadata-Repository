(ns cmr.search.system
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.common.api.web-server :as web]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [clojure.core.cache :as clj-cache]
            [cmr.acl.acl-fetcher :as af]
            [cmr.acl.core :as acl]
            [cmr.common.jobs :as jobs]
            [cmr.search.api.routes :as routes]
            [cmr.search.data.elastic-search-index :as idx]
            [cmr.search.services.acls.acl-helper :as ah]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.transmit.config :as transmit-config]
            [cmr.elastic-utils.config :as es-config]
            [cmr.search.services.query-execution.has-granules-results-feature :as hgrf]
            [cmr.search.services.acls.collections-cache :as coll-cache]
            [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.search.services.transformer :as transformer]))

;; Design based on http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts

(def TOKEN_CACHE_TIME
  "The number of milliseconds token information will be cached for."
  (* 5 60 1000))

(defconfig search-public-protocol
  "The protocol to use in links returned by the search application."
  {:default "http"})

(defconfig search-public-host
  "The host name to use in links returned by the search application."
  {:default "localhost"})

(defconfig search-public-port
  "The port to use in links returned by the search application."
  {:default 3003
   :type Long})

(defconfig search-nrepl-port
  "Port to listen for nREPL connections"
  {:default nil
   :parser cfg/maybe-long})

(def search-public-conf
  {:protocol (search-public-protocol)
   :host (search-public-host)
   :port (search-public-port)
   :relative-root-url (transmit-config/search-relative-root-url)})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :caches :search-index :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [metadata-db (-> (mdb-system/create-system "metadata-db-in-search-app-pool")
                        (dissoc :log :web :scheduler :queue-broker))
        sys {:log (log/create-logger)

             ;; An embedded version of the metadata db app to allow quick retrieval of data
             ;; from oracle.
             :embedded-systems {:metadata-db metadata-db}
             :search-index (idx/create-elastic-search-index (es-config/elastic-config))
             :web (web/create-web-server (transmit-config/search-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (search-nrepl-port))
             ;; Caches added to this list must be explicitly cleared in query-service/clear-cache
             :caches {idx/index-cache-name (mem-cache/create-in-memory-cache)
                      af/acl-cache-key (af/create-acl-cache
                                         (stl-cache/create-single-thread-lookup-cache)
                                         [:catalog-item :system-object])
                      ;; Caches a map of tokens to the security identifiers
                      ah/token-sid-cache-name (mem-cache/create-in-memory-cache :ttl {} {:ttl TOKEN_CACHE_TIME})
                      :has-granules-map (hgrf/create-has-granules-map-cache)
                      coll-cache/cache-key (coll-cache/create-cache)
                      transformer/xsl-transformer-cache-name (mem-cache/create-in-memory-cache)
                      acl/token-imp-cache-key (acl/create-token-imp-cache)
                      ;; Note that search does not have a job to refresh the KMS cache. The indexer
                      ;; already refreshes the cache. Since we use a consistent cache, the search
                      ;; application will also pick up the updated KMS keywords.
                      kf/kms-cache-key (kf/create-kms-cache)}
             :search-public-conf search-public-conf
             :scheduler (jobs/create-scheduler
                          `system-holder
                          :db
                          [(af/refresh-acl-cache-job "search-acl-cache-refresh")
                           hgrf/refresh-has-granules-map-job
                           coll-cache/refresh-collections-cache-for-granule-acls-job])}]
    (transmit-config/system-with-connections sys [:index-set :echo-rest :metadata-db :kms :cubby])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               (update-in this [:embedded-systems :metadata-db] mdb-system/start)
                               component-order)]
    (info "System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))
        stopped-system (update-in stopped-system [:embedded-systems :metadata-db] mdb-system/stop)]
    (info "System stopped")
    stopped-system))

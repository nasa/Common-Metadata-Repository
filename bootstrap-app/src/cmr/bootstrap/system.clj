(ns cmr.bootstrap.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require
   [cmr.access-control.system :as ac-system]
   [cmr.acl.core :as acl]
   [cmr.bootstrap.api.routes :as routes]
   [cmr.bootstrap.config :as bootstrap-config]
   [cmr.bootstrap.data.bulk-index :as bi]
   [cmr.bootstrap.data.bulk-migration :as bm]
   [cmr.bootstrap.data.virtual-products :as vp]
   [cmr.bootstrap.services.dispatch.core :as dispatch]
   [cmr.common-app.api.health :as common-health]
   [cmr.common-app.services.jvm-info :as jvm-info]
   [cmr.common-app.services.kms-fetcher :as kf]
   [cmr.common.api.web-server :as web]
   [cmr.common.cache :as cache]
   [cmr.common.cache.in-memory-cache :as mem-cache]
   [cmr.common.config :as cfg :refer [defconfig]]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.nrepl :as nrepl]
   [cmr.common.system :as common-sys]
   [cmr.indexer.data.concepts.granule :as g]
   [cmr.indexer.system :as idx-system]
   [cmr.message-queue.queue.queue-broker :as queue-broker]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.system :as mdb-system]
   [cmr.oracle.connection :as oracle]
   [cmr.transmit.config :as transmit-config]))

(defconfig db-batch-size
  "Batch size to use when batching database operations."
  {:default 100 :type Long})

(defconfig log-level
  "App logging level"
  {:default "info"})

(def ^:private component-order
  "Defines the order to start the components."
  [:log :caches :db :queue-broker :scheduler :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [metadata-db (-> "metadata-db-in-bootstrap-pool"
                        (mdb-system/create-system)
                        (dissoc :log :web :scheduler :unclustered-scheduler :queue-broker))
        indexer (-> (idx-system/create-system)
                    (dissoc :log :web :queue-broker)
                    ;; Setting the parent-collection-cache to cache parent collection umm
                    ;; of granules during bulk indexing.
                    (assoc-in [:caches g/parent-collection-cache-key]
                              (mem-cache/create-in-memory-cache :lru {} {:threshold 2000}))
                    ;; Specify an Elasticsearch http retry handler
                    (assoc-in [:db :config :retry-handler] bi/elastic-retry-handler))
        queue-broker (queue-broker/create-queue-broker (bootstrap-config/queue-config))
        sys {:log (log/create-logger-with-log-level (log-level))
             :embedded-systems {:metadata-db metadata-db
                                :indexer indexer}
             :db-batch-size (db-batch-size)
             :core-async-dispatcher (dispatch/create-backend :async)
             :synchronous-dispatcher (dispatch/create-backend :sync)
             :message-queue-dispatcher (dispatch/create-backend :message-queue)
             :catalog-rest-user (mdb-config/catalog-rest-db-username)
             :db (oracle/create-db (bootstrap-config/db-spec "bootstrap-pool"))
             :web (web/create-web-server (transmit-config/bootstrap-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (bootstrap-config/bootstrap-nrepl-port))
             :relative-root-url (transmit-config/bootstrap-relative-root-url)
             :caches {acl/token-imp-cache-key (acl/create-token-imp-cache)
                      kf/kms-cache-key (kf/create-kms-cache)
                      common-health/health-cache-key (common-health/create-health-cache)}
             :scheduler (jobs/create-scheduler `system-holder [jvm-info/log-jvm-statistics-job])
             :queue-broker queue-broker}]
    (transmit-config/system-with-connections sys [:metadata-db :echo-rest :kms
                                                  :indexer :access-control])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "Bootstrap system starting")
  (let [;; Need to start indexer first so the connection will be in the context of synchronous
        ;; bulk index requests
        started-system (update-in this [:embedded-systems :indexer] idx-system/start)
        started-system (update-in started-system [:embedded-systems :metadata-db] mdb-system/start)
        started-system (common-sys/start started-system component-order)]
    (bm/handle-copy-requests started-system)
    (bi/handle-bulk-index-requests started-system)
    (vp/handle-virtual-product-requests started-system)
    (when (:queue-broker this)
      (dispatch/subscribe-to-events {:system started-system}))
    (info "Bootstrap system started")
    started-system))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "Bootstrap system shutting down")
  (let [stopped-system (common-sys/stop this component-order)
        stopped-system (update-in stopped-system [:embedded-systems :metadata-db] mdb-system/stop)
        stopped-system (update-in stopped-system [:embedded-systems :indexer] idx-system/stop)]
    (info "Bootstrap system stopped")
    stopped-system))

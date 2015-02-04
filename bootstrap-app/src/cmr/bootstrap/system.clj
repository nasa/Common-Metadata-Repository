(ns cmr.bootstrap.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.bootstrap.api.routes :as routes]
            [cmr.common.api.web-server :as web]
            [cmr.oracle.connection :as oracle]
            [cmr.system-trace.context :as context]
            [clojure.core.async :as ca :refer [chan]]
            [cmr.bootstrap.data.bulk-migration :as bm]
            [cmr.bootstrap.data.bulk-index :as bi]
            [cmr.bootstrap.data.db-synchronization :as dbs]
            [cmr.bootstrap.services.jobs :as bootstrap-jobs]
            [cmr.metadata-db.config :as mdb-config]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.jobs :as jobs]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as idx-system]
            [cmr.indexer.data.concepts.granule :as g]
            [cmr.common.cache :as cache]
            [cmr.common.config :as cfg]))

(def db-batch-size (cfg/config-value-fn :db-batch-size 100 #(Long. %)))

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :db :web :scheduler])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [metadata-db (-> (mdb-system/create-system "metadata-db-in-bootstrap-pool")
                        (dissoc :log :web :scheduler))
        indexer (-> (idx-system/create-system)
                    (dissoc :log :web :queue-broker :queue-listener)
                    ;; Setting the parent-collection-cache to cache parent collection umm
                    ;; of granules during bulk indexing.
                    (assoc-in [:caches g/parent-collection-cache-key]
                              (cache/create-cache :lru {} {:threshold 2000}))
                    ;; Specify an Elasticsearch http retry handler
                    (assoc-in [:db :config :retry-handler] bi/elastic-retry-handler))
        sys {:log (log/create-logger)
             :metadata-db metadata-db
             :indexer indexer
             :db-batch-size (db-batch-size)

             ;; Channel for requesting full provider migration - provider/collections/granules.
             ;; Takes single provider-id strings.
             :provider-db-channel (chan 10)
             ;; Channel for requesting single collection/granules migration.
             ;; Takes maps, e.g., {:collection-id collection-id :provider-id provider-id}
             :collection-db-channel (chan 100)

             ;; Channel for requesting full provider indexing - collections/granules
             :provider-index-channel (chan 10)

             ;; Channel for processing collections to index.
             :collection-index-channel (chan 100)

             ;; Channel for asynchronously sending database synchronization requests
             :db-synchronize-channel (chan)

             :catalog-rest-user (mdb-config/catalog-rest-db-username)
             :db (oracle/create-db (mdb-config/db-spec "bootstrap-pool"))
             :web (web/create-web-server (transmit-config/bootstrap-port) routes/make-api)
             ;; Uncomment the following line to enable db synchronization job
             ;:scheduler (jobs/create-clustered-scheduler `system-holder bootstrap-jobs/jobs)
             :zipkin (context/zipkin-config "bootstrap" false)
             :relative-root-url (transmit-config/bootstrap-relative-root-url)}]
    (transmit-config/system-with-connections sys [:metadata-db])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "bootstrap System starting")
  (let [;; Need to start indexer first so the connection will be in the context of synchronous
        ;; bulk index requests
        started-system (update-in this [:indexer] idx-system/start)
        started-system (update-in started-system [:metadata-db] mdb-system/start)
        started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               started-system
                               component-order)]

    (oracle/test-db-connection! (:db started-system))
    (bm/handle-copy-requests started-system)
    (bi/handle-bulk-index-requests started-system)
    (dbs/handle-db-synchronization-requests started-system)
    (info "Bootstrap System started")
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "bootstrap System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               this
                               (reverse component-order))
        stopped-system (update-in stopped-system [:metadata-db] mdb-system/stop)
        stopped-system (update-in stopped-system [:indexer] idx-system/stop)]
    (info "bootstrap System stopped")
    stopped-system))

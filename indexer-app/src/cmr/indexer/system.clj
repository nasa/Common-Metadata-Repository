(ns cmr.indexer.system
  "Defines functions for creating, starting, and stopping the application. Applications are
  represented as a map of components. Design based on
  http://stuartsierra.com/2013/09/15/lifecycle-composition and related posts."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.nrepl :as nrepl]
            [cmr.system-trace.context :as context]
            [cmr.common.api.web-server :as web]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.indexer.config :as config]
            [cmr.common.cache :as cache]
            [cmr.common.cache.in-memory-cache :as mem-cache]
            [cmr.common.cache.single-thread-lookup-cache :as stl-cache]
            [cmr.acl.acl-fetcher :as af]
            [cmr.common.jobs :as jobs]
            [cmr.indexer.api.routes :as routes]
            [cmr.transmit.config :as transmit-config]
            [clojure.string :as str]
            [cmr.common.config :as cfg :refer [defconfig]]
            [cmr.elastic-utils.config :as es-config]
            [cmr.acl.core :as acl]
            [cmr.message-queue.services.queue :as queue]
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.message-queue.config :as rmq-conf]
            [cmr.indexer.services.queue-listener :as ql]
            [cmr.common-app.cache.consistent-cache :as consistent-cache]))

(defconfig colls-with-separate-indexes
  "Configuration value that contains a list of collections with separate indexes for their
  granule data.  The collections are comma separated."
  {:default [] :parser #(str/split % #",")})

(def
  ^{:doc "Defines the order to start the components."
    :private true}
  component-order [:log :caches :db :scheduler :queue-broker :queue-listener :web :nrepl])

(def system-holder
  "Required for jobs"
  (atom nil))

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [sys {:log (log/create-logger)
             :db (es/create-elasticsearch-store (es-config/elastic-config))
             ;; This is set as a dynamic lookup to enable easy replacement of the value for testing.
             :colls-with-separate-indexes-fn colls-with-separate-indexes
             :web (web/create-web-server (transmit-config/indexer-port) routes/make-api)
             :nrepl (nrepl/create-nrepl-if-configured (config/indexer-nrepl-port))
             :zipkin (context/zipkin-config "Indexer" false)
             :relative-root-url (transmit-config/indexer-relative-root-url)
             :caches {af/acl-cache-key (af/create-acl-cache
                                         (stl-cache/create-single-thread-lookup-cache
                                           (consistent-cache/create-consistent-cache))
                                         [:catalog-item :system-object :provider-object])
                      cache/general-cache-key (mem-cache/create-in-memory-cache)
                      acl/token-imp-cache-key (acl/create-token-imp-cache)}
             :scheduler (jobs/create-scheduler
                          `system-holder
                          :db
                          [(af/refresh-acl-cache-job "indexer-acl-cache-refresh")])
             :queue-broker (when (config/use-index-queue?)
                             (rmq/create-queue-broker (assoc (rmq-conf/default-config)
                                                             :queues [(config/index-queue-name)])))
             :queue-listener (when (config/use-index-queue?)
                               (queue/create-queue-listener {:num-workers (config/queue-listener-count)
                                                             :start-function #(ql/start-queue-message-handler
                                                                                %
                                                                                ql/handle-index-action)}))}]

    (transmit-config/system-with-connections sys [:metadata-db :index-set :echo-rest :cubby])))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (info "System starting")
  (let [started-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/start % system))))
                               system
                               component-order)]

    (info "System started")
    started-system))

(defn dev-start
  "Starts the system but performs extra calls to make sure all indexes are created in elastic."
  [system]
  (let [started-system (start system)
        context {:system started-system}]
    ;; The indexes will not be created if they already exist.
    (es/create-indexes context)
    (when (es/requires-update? context)
      (es/update-indexes context))
    started-system))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (info "System shutting down")
  (let [stopped-system (reduce (fn [system component-name]
                                 (update-in system [component-name]
                                            #(when % (lifecycle/stop % system))))
                               system
                               (reverse component-order))]
    (info "System stopped")
    stopped-system))

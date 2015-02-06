(ns cmr.dev-system.system
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.system :as bootstrap-system]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as indexer-system]
            [cmr.indexer.config :as iconfig]
            [cmr.search.system :as search-system]
            [cmr.ingest.system :as ingest-system]
            [cmr.ingest.data.provider-acl-hash :as ingest-data]
            [cmr.index-set.system :as index-set-system]
            [cmr.mock-echo.system :as mock-echo-system]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.index-set.data.elasticsearch :as es-index]
            [cmr.search.data.elastic-search-index :as es-search]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.spatial.dev.viz-helper :as viz-helper]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
            [cmr.common.config :as config]
            [cmr.indexer.services.queue-listener :as ql]
            [cmr.message-queue.config :as rmq-conf]
            [cmr.message-queue.queue.memory-queue :as memory-queue]
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.dev-system.queue-broker-wrapper :as wrapper]
            [cmr.dev-system.control :as control]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.api.web-server :as web]))

(def app-control-functions
  "A map of application name to the start function"
  {:mock-echo {:start mock-echo-system/start
               :stop mock-echo-system/stop}
   :metadata-db {:start mdb-system/start
                 :stop mdb-system/stop}
   :index-set {:start index-set-system/start
               :stop index-set-system/stop}
   :indexer {:start indexer-system/start
             :stop indexer-system/stop}
   :ingest {:start ingest-system/start
            :stop ingest-system/stop}
   :search {:start search-system/start
            :stop search-system/stop}
   :bootstrap {:start bootstrap-system/start
               :stop bootstrap-system/stop}})

(def app-startup-order
  "Defines the order in which applications should be started"
  [:mock-echo :metadata-db :index-set :indexer :ingest :search :bootstrap])

(def in-memory-elastic-port 9206)

(def in-memory-elastic-port-for-connection 9206)

(def use-compression?
  "Indicates whether the servers will use gzip compression. Disable this to make tcpmon usable"
  true)

(def use-access-log?
  "Indicates whether the servers will use the access log."
  false)

(defn- set-web-server-options
  "Modifies the app server instances to configure web server options. Takes the system
  and returns it with the updates made. Should be run a system before it is started"
  [system]
  (update-in system [:apps]
             (fn [app-map]
               (into {} (for [[app-name app-system] app-map]
                          [app-name (-> app-system
                                        (assoc-in [:web :use-compression?] use-compression?)
                                        (assoc-in [:web :use-access-log?] use-access-log?))])))))

(defmulti create-system
  "Returns a new instance of the whole application."
  (fn [type]
    type))

(defmethod create-system :in-memory
  [type]

  ;; Sets a bit of global state for the application and system integration tests that will know how to talk to elastic
  (config/set-config-value! :elastic-port in-memory-elastic-port-for-connection)
  ;; The same in memory db is used for metadata db by itself and in search so they contain the same data
  ;; The same in-memory queue is sued for indexer and ingest for the same reason
  (let [in-memory-db (memory/create-db)
        memory-queue-broker (when (iconfig/use-index-queue?)
                              (memory-queue/create-queue-broker [(iconfig/index-queue-name)]))
        control-server (web/create-web-server 2999 control/make-api use-compression? use-access-log?)]
    {:apps {:mock-echo (mock-echo-system/create-system)
            :metadata-db (-> (mdb-system/create-system)
                             (assoc :db in-memory-db)
                             (dissoc :scheduler))
            ;; Bootstrap is not enabled for in-memory dev system
            :indexer (assoc (indexer-system/create-system) :queue-broker memory-queue-broker)
            :index-set (index-set-system/create-system)
            :ingest (-> (ingest-system/create-system)
                        (assoc :db (ingest-data/create-in-memory-acl-hash-store))
                        (assoc :queue-broker memory-queue-broker)
                        (dissoc :scheduler))
            :search (assoc-in (search-system/create-system)
                              [:metadata-db :db]
                              in-memory-db)}
     :pre-components {:elastic-server (elastic-server/create-server
                                        in-memory-elastic-port
                                        (+ in-memory-elastic-port 10)
                                        "es_data/dev_system")}
     :post-components {:control-server control-server
                       ; :vdd-server (viz-helper/create-viz-server)
                       }}))

(defmethod create-system :external-dbs
  [type]
  (let [control-server (web/create-web-server 2999 control/make-api use-compression? use-access-log?)
        queue-broker (rmq/create-queue-broker (assoc (rmq-conf/default-config)
                                                     :queues
                                                     [(iconfig/index-queue-name)]))
        broker-wrapper (wrapper/create-queue-broker-wrapper queue-broker)
        listener-start-fn #(ql/start-queue-message-handler
                             %
                             (wrapper/handler-wrapper broker-wrapper ql/handle-index-action))
        queue-listener (queue/create-queue-listener
                         {:num-workers (iconfig/queue-listener-count)
                          :start-function listener-start-fn})]
    {:apps {:mock-echo (mock-echo-system/create-system)
            :metadata-db (mdb-system/create-system)
            :bootstrap (bootstrap-system/create-system)
            :indexer (let [indexer (indexer-system/create-system)]
                       (if (iconfig/use-index-queue?)
                         (assoc indexer
                                :queue-broker broker-wrapper
                                :queue-listener queue-listener)
                         indexer))
            :index-set (index-set-system/create-system)
            :ingest (let [ingest (ingest-system/create-system)]
                      (if (iconfig/use-index-queue?)
                        (assoc ingest :queue-broker broker-wrapper)
                        ingest))
            :search (search-system/create-system)}
     :pre-components {:broker-wrapper broker-wrapper}
     :post-components {
                       ; :vdd-server (viz-helper/create-viz-server)
                       :control-server control-server
                       }}))

(defn- stop-components
  [system components-key]
  (reduce (fn [system component]
            (update-in system [components-key component]
                       #(lifecycle/stop % system)))
          system
          (keys (components-key system))))

(defn- stop-apps
  [system]
  (reduce (fn [system app]
            (let [{stop-fn :stop} (app-control-functions app)]
              (update-in system [:apps app] #(when % (stop-fn %)))))
          system
          (reverse app-startup-order)))

(defn- start-components
  [system components-key]
  (reduce (fn [system component]
            (update-in system [components-key component]
                       #(try
                          (lifecycle/start % system)
                          (catch Exception e
                            (error e "Failure during startup")
                            (stop-components (stop-apps system) :pre-components)
                            (stop-components (stop-apps system) :post-components)
                            (throw e)))))
          system
          (keys (components-key system))))

(defn- start-apps
  [system]
  (let [system (set-web-server-options system)]
    (reduce (fn [system app]
              (let [{start-fn :start} (app-control-functions app)]
                (update-in system [:apps app]
                           #(try
                              (when %
                                (start-fn %))
                              (catch Exception e
                                (error e "Failure during startup")
                                (stop-components (stop-apps system) :pre-components)
                                (stop-components (stop-apps system) :post-components)
                                (throw e))))))
            system
            app-startup-order)))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")

  (-> this
      (start-components :pre-components)
      start-apps
      (start-components :post-components)
      ))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")

  (-> this
      (stop-components :post-components)
      stop-apps
      (stop-components :pre-components)))

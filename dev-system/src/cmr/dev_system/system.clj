(ns cmr.dev-system.system
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.system :as bootstrap-system]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as indexer-system]
            [cmr.indexer.config :as indexer-config]
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
            [cmr.message-queue.queue.rabbit-mq :as rmq]
            [cmr.dev-system.queue-broker-wrapper :as wrapper]
            [cmr.dev-system.control :as control]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.api.web-server :as web]
            [cmr.common.util :as u]))

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

(def external-elastic-port 9210)

(def in-memory-echo-port 3008)

(def external-echo-port 10000)

(def in-memory-echo-system-token "mock-echo-system-token")

(def external-echo-system-token
  "Returns the ECHO system token based on the value for ECHO_SYSTEM_READ_TOKEN in the ECHO
  configuration file.  The WORKSPACE_HOME environment variable must be set in order to find the
  file.  Returns nil if it cannot extract the value."
  (try
    (->> (slurp (str (System/getenv "WORKSPACE_HOME") "/deployment/primary/config.properties"))
         (re-find #"\n@ECHO_SYSTEM_READ_TOKEN@=(.*)\n")
         peek)
    (catch Exception e
      (warn "Unable to extract the ECHO system read token from configuration.")
      nil)))

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


(defmulti create-elastic
  "Sets elastic configuration values and returns an instance of an Elasticsearch component to run
  in memory if applicable."
  (fn [type]
    type))

(defmethod create-elastic :in-memory
  [type]
  (config/set-config-value! :elastic-port in-memory-elastic-port)
  (elastic-server/create-server
    in-memory-elastic-port
    (+ in-memory-elastic-port 10)
    "es_data/dev_system"))

(defmethod create-elastic :external
  [type]
  (config/set-config-value! :elastic-port external-elastic-port)
  nil)

(defmulti create-db
  "Returns an instance of the database component to use."
  (fn [type]
    type))

(defmethod create-db :in-memory
  [type]
  (memory/create-db))

(defmethod create-db :external
  [type]
  nil)

(defmulti create-echo
  "Sets ECHO configuration values and returns an instance of a mock ECHO component to run in
  memory if applicable."
  (fn [type]
    type))

(defmethod create-echo :in-memory
  [type]
  (config/set-config-value! :echo-rest-port in-memory-echo-port)
  (config/set-config-value! :echo-system-token in-memory-echo-system-token)
  (config/set-config-value! :echo-rest-context "")
  (mock-echo-system/create-system))

(defmethod create-echo :external
  [type]
  (config/set-config-value! :echo-rest-port external-echo-port)
  (config/set-config-value! :echo-system-token external-echo-system-token)
  (config/set-config-value! :echo-rest-context "/echo-rest")
  nil)

(defmulti create-queue-broker
  "Sets message queue configuration values and returns an instance of the message queue broker
  if applicable."
  (fn [type]
    type))

(defmethod create-queue-broker :in-memory
  [type]
  ;; Ingest and the indexer will not use a message queue for in-memory tests
  (config/set-config-value! :indexing-communication-method "http")
  nil)

(defn rmq-default-config
  "The default config for connecting to RabbitMQ locally"
  []
  {:port (rmq-conf/rabbit-mq-port)
   :host (rmq-conf/rabbit-mq-host)
   :username (rmq-conf/rabbit-mq-user)
   :password (rmq-conf/rabbit-mq-password)
   :ttls [1 1 1 1 1]})

(defmethod create-queue-broker :external
  [type]
  (config/set-config-value! :indexing-communication-method "queue")
  (-> (rmq/create-queue-broker (assoc (rmq-default-config)
                                      :queues
                                      [(indexer-config/index-queue-name)]))
      wrapper/create-queue-broker-wrapper))

(defmulti create-queue-listener
  "Returns an instance of the message queue listener component to use if using a message queue."
  (fn [type queue-broker]
    type))

(defmethod create-queue-listener :in-memory
  [type queue-broker]
  nil)

(indexer-config/queue-listener-count)
(defmethod create-queue-listener :external
  [type queue-broker]
  (let [listener-start-fn #(ql/start-queue-message-handler
                             %
                             (wrapper/handler-wrapper queue-broker ql/handle-index-action))]
    (queue/create-queue-listener
      {:num-workers (indexer-config/queue-listener-count)
       :start-function listener-start-fn})))

(defn create-metadata-db-app
  "Create an instance of the metadata-db application."
  [db-component]
  (if db-component
    (-> (mdb-system/create-system)
        (assoc :db db-component)
        (dissoc :scheduler))
    (mdb-system/create-system)))

(defn create-indexer-app
  "Create an instance of the indexer application."
  [queue-broker queue-listener]
  (if queue-broker
    (-> (indexer-system/create-system)
        (assoc :queue-broker queue-broker
               :queue-listener queue-listener))
    (indexer-system/create-system)))

(defmulti create-ingest-app
  "Create an instance of the ingest application."
  (fn [db-type queue-broker]
    db-type))

(defmethod create-ingest-app :in-memory
  [db-type queue-broker]
  (-> (ingest-system/create-system)
      (assoc :db (ingest-data/create-in-memory-acl-hash-store)
             :queue-broker queue-broker)
      (dissoc :scheduler)))

(defmethod create-ingest-app :external
  [db-type queue-broker]
  (-> (ingest-system/create-system)
      (assoc :queue-broker queue-broker)))


(defn create-search-app
  "Create an instance of the search application."
  [db-component]
  (if db-component
    (assoc-in (search-system/create-system)
              [:metadata-db :db]
              db-component)
    (search-system/create-system)))

(defn create-system
  "Returns a new instance of the whole application. Takes a map:
  {:elastic :in-memory
  :echo :in-memory
  :db :external
  :message-queue :external}"
  [component-type-map]
  (let [{:keys [elastic echo db message-queue]} component-type-map
        db-component (create-db db)
        echo-component (create-echo echo)
        queue-broker (create-queue-broker message-queue)
        queue-listener (create-queue-listener message-queue queue-broker)
        elastic-server (create-elastic elastic)
        control-server (web/create-web-server 2999 control/make-api use-compression? use-access-log?)]
    {:apps (u/remove-nil-keys
             {:mock-echo echo-component
              :metadata-db (create-metadata-db-app db-component)
              :bootstrap (when-not db-component (bootstrap-system/create-system))
              :indexer (create-indexer-app queue-broker queue-listener)
              :index-set (index-set-system/create-system)
              :ingest (create-ingest-app db queue-broker)
              :search (create-search-app db-component)})
     :pre-components (u/remove-nil-keys
                       {:elastic-server elastic-server
                        :broker-wrapper queue-broker})
     :post-components {:control-server control-server
                       ; :vdd-server (viz-helper/create-viz-server)
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

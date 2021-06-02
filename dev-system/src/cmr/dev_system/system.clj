(ns cmr.dev-system.system
  (:require
   [cmr.access-control.config :as access-control-config]
   [cmr.access-control.system :as access-control-system]
   [cmr.bootstrap.config :as bootstrap-config]
   [cmr.bootstrap.system :as bootstrap-system]
   [cmr.common.jobs :as jobs]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.log :refer [debug info warn error]]
   [cmr.common.util :as u]
   [cmr.dev-system.config :as dev-config]
   [cmr.dev-system.control :as control]
   [cmr.elastic-utils.config :as elastic-config]
   [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
   [cmr.indexer.config :as indexer-config]
   [cmr.indexer.system :as indexer-system]
   [cmr.ingest.config :as ingest-config]
   [cmr.ingest.data.memory-db :as ingest-data]
   [cmr.ingest.system :as ingest-system]
   [cmr.message-queue.config :as rmq-conf]
   [cmr.message-queue.queue.memory-queue :as mem-queue]
   [cmr.message-queue.queue.sqs :as sqs]
   [cmr.message-queue.test.queue-broker-wrapper :as wrapper]
   [cmr.metadata-db.data.memory-db :as memory]
   [cmr.metadata-db.system :as mdb-system]
   [cmr.mock-echo.system :as mock-echo-system]
   [cmr.redis-utils.config :as redis-config]
   [cmr.redis-utils.embedded-redis-server :as redis-server]
   [cmr.search.system :as search-system]
   [cmr.transmit.config :as transmit-config]
   [cmr.virtual-product.config :as vp-config]
   [cmr.virtual-product.system :as vp-system]))

(defn external-echo-system-token
  "Returns the ECHO system token based on the value for ECHO_SYSTEM_READ_TOKEN in the ECHO
  configuration file. The WORKSPACE_HOME environment variable must be set in order to find the
  file. Returns mock-echo-system-token if it cannot extract the value."
  []
  (try
    (let [token (->> (str (or (System/getenv "WORKSPACE_HOME")
                              "../../")
                          "/deployment/primary/config.properties")
                     slurp
                     (re-find #"\n@ECHO_SYSTEM_READ_TOKEN@=(.*)\n")
                     peek)]
      (info "Using system token" token)
      token)
    (catch Exception e
      (warn "Unable to extract the ECHO system read token from configuration.")
      transmit-config/mock-echo-system-token)))

(def app-control-functions
  "A map of application name to the start function"
  {:mock-echo {:start mock-echo-system/start
               :stop mock-echo-system/stop}
   :metadata-db {:start mdb-system/start
                 :stop mdb-system/stop}
   :indexer {:start indexer-system/dev-start
             :stop indexer-system/stop}
   :ingest {:start ingest-system/start
            :stop ingest-system/stop}
   :search {:start search-system/start
            :stop search-system/stop}
   :bootstrap {:start bootstrap-system/start
               :stop bootstrap-system/stop}
   :access-control {:start access-control-system/dev-start
                    :stop access-control-system/stop}
   :virtual-product {:start vp-system/start
                     :stop vp-system/stop}})

(def app-startup-order
  "Defines the order in which applications should be started"
  [:mock-echo :metadata-db :access-control :indexer :ingest :search :virtual-product :bootstrap])

(defn- update-app-web-server-options
  "Update the web configuration options for the passed app system."
  [app-system]
  (-> app-system
      (assoc-in [:web :use-compression?] (dev-config/use-web-compression?))
      (assoc-in [:web :use-access-log?] (dev-config/use-access-log))))

(defn- set-web-server-options
  "Modifies an app server instance to configure web server options, returning a
  key/value pair that is the new (updated) app map."
  [app-map]
  (->> app-map
       (map (fn [[app-name app-system]]
              [app-name (update-app-web-server-options app-system)]))
       (into {})))

(defn- set-all-web-server-options
  "Modifies all app server instances to configure web server options. Takes the system
  and returns it with the updates made. Should be run a system before it is started."
  [system]
  (update-in system [:apps] set-web-server-options))

(def in-memory-elastic-log-level-atom
  (atom :info))

(defmulti create-elastic
  "Sets elastic configuration values and returns an instance of an Elasticsearch component to run
  in memory if applicable."
  (fn [type]
    type))

(defmethod create-elastic :in-memory
  [_]
  (let [http-port (elastic-config/elastic-port)]
    (elastic-server/create-server http-port
                                  {:log-level (name @in-memory-elastic-log-level-atom)
                                   :kibana-port (dev-config/embedded-kibana-port)
                                   :image-cfg {"Dockerfile" "elasticsearch/Dockerfile.elasticsearch"
                                               "es_libs" "elasticsearch/es_libs"
                                               "embedded-security.policy" "elasticsearch/embedded-security.policy"
                                               "plugins" "elasticsearch/plugins"}})))

(defmethod create-elastic :external
  [_]
  (elastic-config/set-elastic-port! 9209))

(defmulti create-redis
  "Sets redis configuration values and returns an instance of a Redis component to run
  in memory if applicable."
  (fn [type]
    type))

(defmethod create-redis :in-memory
  [_]
  (let [port (redis-config/redis-port)]
    (redis-server/create-redis-server port)))

(defmethod create-redis :external
  [_]
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
  (transmit-config/set-urs-relative-root-url! "/urs")
  (mock-echo-system/create-system))

(defmethod create-echo :external
  [type]
  (transmit-config/set-echo-rest-port! (dev-config/external-echo-port))
  (transmit-config/set-echo-system-token! (external-echo-system-token))
  (transmit-config/set-echo-rest-context! "/echo-rest"))

(defmulti create-queue-broker
  "Sets message queue configuration values and returns an instance of the message queue broker
  if applicable."
  (fn [type]
    type))

(defmethod create-queue-broker :in-memory
  [type]
  (-> (indexer-config/queue-config)
      (rmq-conf/merge-configs (vp-config/queue-config))
      (rmq-conf/merge-configs (access-control-config/queue-config))
      (rmq-conf/merge-configs (ingest-config/queue-config))
      (rmq-conf/merge-configs (bootstrap-config/queue-config))
      mem-queue/create-memory-queue-broker
      wrapper/create-queue-broker-wrapper))

(defn- external-queue-config
  "Create a configuration for an external queue (Rabbit MQ or AWS)."
  [ttls]
  (-> (indexer-config/queue-config)
      (rmq-conf/merge-configs (vp-config/queue-config))
      (rmq-conf/merge-configs (access-control-config/queue-config))
      (rmq-conf/merge-configs (ingest-config/queue-config))
      (rmq-conf/merge-configs (bootstrap-config/queue-config))
      (assoc :ttls ttls)))

(defmethod create-queue-broker :aws
  [type]
  (-> (external-queue-config [])
      sqs/create-queue-broker))

(defn create-metadata-db-app
  "Create an instance of the metadata-db application."
  [db-component queue-broker]
  (let [sys-with-db (if db-component
                      (assoc (mdb-system/create-system)
                             :db db-component
                             :scheduler (jobs/create-non-running-scheduler))
                      (mdb-system/create-system))]
    (assoc sys-with-db :queue-broker queue-broker)))

(defn create-indexer-app
  "Create an instance of the indexer application."
  [queue-broker]
  (assoc (indexer-system/create-system) :queue-broker queue-broker))

(defn create-virtual-product-app
  "Create an instance of the virtual product application."
  [queue-broker]
  (assoc (vp-system/create-system) :queue-broker queue-broker))

(defn create-access-control-app
  "Create an instance of the access control application."
  [queue-broker]
  (assoc (access-control-system/create-system) :queue-broker queue-broker))

(defn create-bootstrap-app
  "Create an instance of the bootstrap application."
  [queue-broker]
  (assoc (bootstrap-system/create-system) :queue-broker queue-broker))

(defmulti create-ingest-app
  "Create an instance of the ingest application."
  (fn [db-type queue-broker]
    db-type))

(defmethod create-ingest-app :in-memory
  [db-type queue-broker]
  (assoc (ingest-system/create-system)
         :db (ingest-data/create-db)
         :queue-broker queue-broker
         :scheduler (jobs/create-non-running-scheduler)))

(defmethod create-ingest-app :external
  [db-type queue-broker]
  (assoc (ingest-system/create-system)
         :queue-broker queue-broker))

(defn create-search-app
  "Create an instance of the search application."
  [db-component queue-broker]
  (let [search-app (if db-component
                     (assoc-in (search-system/create-system)
                               [:embedded-systems :metadata-db :db]
                               db-component)
                     (search-system/create-system))]
    (assoc-in search-app
              [:embedded-systems :metadata-db :queue-broker]
              queue-broker)))

(defn component-type-map
  "Returns a map of dev system components options to run in memory or externally."
  []
  {:elastic (dev-config/dev-system-elastic-type)
   :echo (dev-config/dev-system-echo-type)
   :db (dev-config/dev-system-db-type)
   :message-queue (dev-config/dev-system-queue-type)
   :redis (dev-config/dev-system-redis-type)})

(defn create-system
  "Returns a new instance of the whole application."
  []
  (let [{:keys [elastic echo db message-queue redis]} (component-type-map)
        db-component (create-db db)
        echo-component (create-echo echo)
        queue-broker (create-queue-broker message-queue)
        elastic-server (create-elastic elastic)
        redis-server (create-redis redis)
        control-server (control/create-server)]
    {:apps (u/remove-nil-keys
             {:mock-echo echo-component
              :access-control (create-access-control-app queue-broker)
              :metadata-db (create-metadata-db-app db-component queue-broker)
              :bootstrap (when-not db-component (create-bootstrap-app queue-broker))
              :indexer (create-indexer-app queue-broker)
              :ingest (create-ingest-app db queue-broker)
              :search (create-search-app db-component queue-broker)
              :virtual-product (create-virtual-product-app queue-broker)})
     :pre-components (u/remove-nil-keys
                       {:elastic-server elastic-server
                        :broker-wrapper queue-broker
                        :redis-server redis-server})
     :post-components {:control-server control-server}}))

(defn- stop-components
  [system components-key]
  (reduce (fn [system component]
            (update-in system [components-key component]
                       #(when % (lifecycle/stop % system))))
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
                          (when % (lifecycle/start % system))
                          (catch Exception e
                            (error e "Failure during startup")
                            (stop-components (stop-apps system) :pre-components)
                            (stop-components (stop-apps system) :post-components)
                            (throw e)))))
          system
          (keys (components-key system))))

(defn- start-apps
  [system]
  (let [system (set-all-web-server-options system)]
    (reduce (fn [system app]
              (let [{start-fn :start} (app-control-functions app)]
                (update-in system [:apps app]
                           #(try
                              (when %
                                (start-fn %))
                              (catch Exception e
                                (error e (format "Failure of %s app during startup" app))
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
      (start-components :post-components)))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")

  (-> this
      (stop-components :post-components)
      stop-apps
      (stop-components :pre-components)))

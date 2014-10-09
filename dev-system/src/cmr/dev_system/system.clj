(ns cmr.dev-system.system
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.bootstrap.system :as bootstrap-system]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as indexer-system]
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
            [cmr.dev-system.control :as control]
            [cmr.common.api.web-server :as web]))


(def app-control-functions
  "A map of application name to the start function"
  {:metadata-db {:start mdb-system/start
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
               :stop bootstrap-system/stop}
   :mock-echo {:start mock-echo-system/start
               :stop mock-echo-system/stop}})

(def app-startup-order
  "Defines the order in which applications should be started"
  [:mock-echo :metadata-db :index-set :indexer :ingest :search :bootstrap])

(def in-memory-elastic-port 9206)

(def in-memory-elastic-port-for-connection 9206)

(def use-compression?
  "Indicates whether the servers will use gzip compression. Disable this to make tcpmon usable"
  true)

(defn- set-app-compression
  "Modifies the app server instances to configure the use of compression or not. Takes the system
  and returns it with the updates made. Should be run a system before it is started"
  [system]
  (update-in system [:apps]
             (fn [app-map]
               (into {} (for [[app-name app-system] app-map]
                          [app-name (assoc-in app-system
                                              [:web :use-compression?]
                                              use-compression?)])))))

(defmulti create-system
  "Returns a new instance of the whole application."
  (fn [type]
    type))

(defmethod create-system :in-memory
  [type]

  ;; Sets a bit of global state for the application and system integration tests that will know how to talk to elastic
  (config/set-config-value! :elastic-port in-memory-elastic-port-for-connection)
  ;; The same in memory db is used for metadata db by itself and in search so they contain the same data
  (let [in-memory-db (memory/create-db)
        control-server (web/create-web-server 2999 control/make-api)]
    {:apps {:mock-echo (mock-echo-system/create-system)
            :metadata-db (-> (mdb-system/create-system)
                             (assoc :db in-memory-db)
                             (dissoc :scheduler))
            ;; Bootstrap is not enabled for in-memory dev system
            :indexer (indexer-system/create-system)
            :index-set (index-set-system/create-system)
            :ingest (-> (ingest-system/create-system)
                        (assoc :db (ingest-data/create-in-memory-acl-hash-store))
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
  (let [control-server (web/create-web-server 2999 control/make-api)]
    {:apps {:mock-echo (mock-echo-system/create-system)
            :metadata-db (mdb-system/create-system)
            :bootstrap (bootstrap-system/create-system)
            :indexer (indexer-system/create-system)
            :index-set (index-set-system/create-system)
            :ingest (ingest-system/create-system)
            :search (search-system/create-system)}
     :pre-components {}
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
                            (stop-components (stop-apps system) :pre-components)
                            (stop-components (stop-apps system) :post-components)
                            (throw e)))))
          system
          (keys (components-key system))))

(defn- start-apps
  [system]
  (let [system (set-app-compression system)]
    (reduce (fn [system app]
              (let [{start-fn :start} (app-control-functions app)]
                (update-in system [:apps app]
                           #(try
                              (when %
                                (start-fn %))
                              (catch Exception e
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

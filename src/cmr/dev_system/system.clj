(ns cmr.dev-system.system
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as indexer-system]
            [cmr.search.system :as search-system]
            [cmr.ingest.system :as ingest-system]
            [cmr.metadata-db.data.memory-db :as memory]
            [cmr.indexer.data.elasticsearch :as es-index]
            [cmr.search.data.elastic-search-index :as es-search]
            [cmr.common.lifecycle :as lifecycle]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]))


(def app-control-functions
  "A map of application name to the start function"
  {:metadata-db {:start mdb-system/start
                 :stop mdb-system/stop}
   :indexer {:start indexer-system/start
             :stop indexer-system/stop}
   :ingest {:start ingest-system/start
            :stop ingest-system/stop}
   :search {:start search-system/start
            :stop search-system/stop}})

(def in-memory-elastic-port 9206)

(defmulti create-system
  "Returns a new instance of the whole application."
  (fn [type]
    type))

(defmethod create-system :in-memory
  [type]

  ;; Memory DB configured to run in memory
  {:apps {:metadata-db (assoc (mdb-system/create-system)
                              :db (memory/create-db))


          ;; Indexer will use embedded elastic server
          :indexer (assoc (indexer-system/create-system)
                          :db (es-index/create-elasticsearch-store
                                {:host "localhost"
                                 :port in-memory-elastic-port
                                 :admin-token "none"}))

          :ingest (ingest-system/create-system)

          ;; Search will use the embedded elastic server
          :search (assoc (search-system/create-system)
                         :search-index
                         (es-search/create-elastic-search-index
                           "localhost" in-memory-elastic-port))}
   :components {:elastic-server (elastic-server/create-server
                                  in-memory-elastic-port
                                  (+ in-memory-elastic-port 10))}})

(defmethod create-system :external-dbs
  [type]
  {:apps {:metadata-db (mdb-system/create-system)
          :indexer (indexer-system/create-system)
          :ingest (ingest-system/create-system)
          :search (search-system/create-system)}
   :components {}})

(defn- start-components
  [system]
  (reduce (fn [system component]
            (update-in system [:components component]
                       #(lifecycle/start % system)))
          system
          (keys (:components system))))

(defn- start-apps
  [system]
  (reduce (fn [system [app {start-fn :start}]]
            (update-in system [:apps app] start-fn))
          system
          app-control-functions))

(defn- stop-components
  [system]
  (reduce (fn [system component]
            (update-in system [:components component]
                       #(lifecycle/stop % system)))
          system
          (keys (:components system))))

(defn- stop-apps
  [system]
  (reduce (fn [system [app {stop-fn :stop}]]
            (update-in system [:apps app] stop-fn))
          system
          app-control-functions))


(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")

  (-> this
      start-components
      start-apps))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")

  (-> this
      stop-apps
      stop-components))
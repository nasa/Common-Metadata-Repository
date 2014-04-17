(ns cmr.dev-system.system
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.metadata-db.system :as mdb-system]
            [cmr.indexer.system :as indexer-system]
            [cmr.search.system :as search-system]
            [cmr.ingest.system :as ingest-system]
            [cmr.index-set.system :as index-set-system]))

(def app-control-functions
  "A map of application name to the start function"
  {:metadata-db {:start mdb-system/start
                 :stop mdb-system/stop}
   :index-set-system {:start index-set-system/start
                      :stop index-set-system/stop}
   :indexer {:start indexer-system/start
             :stop indexer-system/stop}
   :ingest {:start ingest-system/start
            :stop ingest-system/stop}
   :search {:start search-system/start
            :stop search-system/stop}})


(defn create-system
  "Returns a new instance of the whole application."
  []
  {:metadata-db (mdb-system/create-system)
   :index-set-system (index-set-system/create-system)
   :indexer (indexer-system/create-system)
   :ingest (ingest-system/create-system)
   :search (search-system/create-system)})

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [this]
  (info "System starting")

  (reduce (fn [system [app {start-fn :start}]]
            (update-in system [app] start-fn))
          this
          app-control-functions))


(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [this]
  (info "System shutting down")

  (reduce (fn [system [app {stop-fn :stop}]]
            (update-in system [app] stop-fn))
          this
          app-control-functions))
(ns cmr.bootstrap.services.health-service
  "Contains fuctions to provider health status of the bootstrap app."
  (:require
   [cmr.bootstrap.embedded-system-helper :as helper]
   [cmr.indexer.services.index-service :as indexer]
   [cmr.metadata-db.services.health-service :as hs]
   [cmr.transmit.metadata-db2 :as mdb]))

(defn health
  "Returns the health state of the app."
  [context]
  (let [metadata-db-health (mdb/get-metadata-db-health context)
        indexer-context (update-in context [:system] helper/get-indexer)
        indexer-health (indexer/health indexer-context)
        internal-meta-db-context (update-in context [:system] helper/get-metadata-db)
        internal-meta-db-health (hs/health internal-meta-db-context)
        ok? (every? :ok? [metadata-db-health internal-meta-db-health indexer-health])]
    {:ok? ok?
     :dependencies {:metadata-db metadata-db-health
                    :internal-metadata-db internal-meta-db-health
                    :indexer indexer-health}}))

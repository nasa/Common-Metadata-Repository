(ns cmr.search.services.health-service
  "Contains fuctions to provider health status of the search app."
  (:require
   [cmr.metadata-db.services.health-service :as meta-db]
   [cmr.transmit.echo.rest :as rest]
   [cmr.transmit.indexer :as indexer]))

(defn health
  "Returns the health state of the app."
  [context]
  (let [echo-rest-health (rest/health context)
        indexer-health (indexer/get-indexer-health context)
        metadata-db-context (assoc context :system
                                   (get-in context [:system :embedded-systems :metadata-db]))
        metadata-db-health (meta-db/health metadata-db-context)
        ok? (every? :ok? [echo-rest-health metadata-db-health indexer-health])]
    {:ok? ok?
     :dependencies {:echo echo-rest-health
                    :internal-metadata-db metadata-db-health
                    :indexer indexer-health}}))

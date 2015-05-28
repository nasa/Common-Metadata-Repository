(ns cmr.search.services.health-service
  "Contains fuctions to provider health status of the search app."
  (:require [cmr.system-trace.core :refer [deftracefn]]
            [cmr.transmit.echo.rest :as rest]
            [cmr.metadata-db.services.health-service :as meta-db]
            [cmr.transmit.index-set :as index-set]))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [echo-rest-health (rest/health context)
        index-set-health (index-set/get-index-set-health context)
        metadata-db-context (assoc context :system
                                   (get-in context [:system :embedded-systems :metadata-db]))
        metadata-db-health (meta-db/health metadata-db-context)
        ok? (every? :ok? [echo-rest-health metadata-db-health index-set-health])]
    {:ok? ok?
     :dependencies {:echo echo-rest-health
                    :internal-metadata-db metadata-db-health
                    :index-set index-set-health}}))
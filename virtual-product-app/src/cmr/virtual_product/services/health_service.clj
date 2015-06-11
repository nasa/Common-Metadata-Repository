(ns cmr.virtual-product.services.health-service
  "Contains fuctions to provider health status of the search app."
  (:require [cmr.system-trace.core :refer [deftracefn]]
            [cmr.transmit.ingest :as ingest]
            [cmr.transmit.metadata-db :as mdb]))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [ingest-health (ingest/get-ingest-health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        ok? (every? :ok? [ingest-health metadata-db-health])]
    {:ok? ok?
     :dependencies {:ingest ingest-health
                    :metadata-db metadata-db-health}}))
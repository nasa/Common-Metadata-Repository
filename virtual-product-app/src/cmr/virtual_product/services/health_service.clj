(ns cmr.virtual-product.services.health-service
  "Contains fuctions to provider health status of the search app."
  (:require
   [cmr.message-queue.queue.queue-protocol :as queue-protocol]
   [cmr.transmit.ingest :as ingest]
   [cmr.transmit.metadata-db2 :as mdb]))

(defn health
  "Returns the health state of the app."
  [context]
  (let [ingest-health (ingest/get-ingest-health context)
        metadata-db-health (mdb/get-metadata-db-health context)
        message-queue-health (queue-protocol/health (get-in context [:system :queue-broker]))
        ok? (every? :ok? [ingest-health metadata-db-health message-queue-health])]
    {:ok? ok?
     :dependencies {:ingest ingest-health
                    :metadata-db metadata-db-health
                    :message-queue message-queue-health}}))

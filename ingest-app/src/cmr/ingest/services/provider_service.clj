(ns cmr.ingest.services.provider-service
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.ingest.data.indexer :as indexer]))

(defn create-provider
  "Create a provider."
  [context provider]
  (mdb/create-provider-raw context provider))

(defn delete-provider
  "Delete a provider and all its concepts."
  [context provider-id]
  (let [response (mdb/delete-provider-raw context provider-id)
        {:keys [status]} response]
    (if (= 200 status)
      (indexer/delete-provider-from-index context provider-id)
      response)))

(defn get-providers
  "Get a list of provider ids"
  [context]
  (mdb/get-providers-raw context))
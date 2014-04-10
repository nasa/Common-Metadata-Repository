(ns cmr.metadata-db.data.provider
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.utility :as util]))

(defn save-provider
  "Saves a provider and returns the provider id. If the provider already 
  exists then an exception is thrown."
  [db provider-id]
  (try (j/insert! db 
                  "METADATA_DB.provider"
                  ["provider_id"]
                  [provider-id])
    {:provider-id provider-id}
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)
            error-code (cond
                         (re-find #"METADATA_DB.UNIQUE_PROVIDER_ID" error-message)
                         :provider-id-conflict
                         
                         :else
                         :unknown-error)]
        {:error error-code :error-message error-message}))))

(defn get-providers
  "Get a sequence of all the providers."
  [db]
  ) 

(defn delete-provider
  "Remove a provider from the database completely, including all of its concepts."
  [db provider-id]
  )

(defn reset-providers
  "Delete all providers from the database.  USE WITH CAUTION."
  [db]
  )
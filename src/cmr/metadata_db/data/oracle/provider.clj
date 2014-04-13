(ns cmr.metadata-db.data.oracle.provider
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.utility :as util]
            [cmr.metadata-db.data.oracle.concept-tables :as ct]))

(defn dbresult->provider-list
  "Convert the sequence of maps coming from the database into a sequence of strings"
  [result]
  (map :provider_id result))

(defn save-provider
  "Saves a provider and returns the provider id. If the provider already 
  exists then an exception is thrown."
  [db provider-id]
  (try (do 
         (j/insert! db 
                    :providers
                    ["provider_id"]
                    [provider-id])
         (ct/create-provider-concept-tables db provider-id))
    {:provider-id provider-id}
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)
            error-code (cond
                         (re-find #"UNIQUE_PROVIDER_ID" error-message)
                         :provider-id-conflict
                         
                         :else
                         :unknown-error)]
        {:error error-code :error-message error-message}))))

(defn get-providers
  "Get a sequence of all the providers."
  [db]
  (dbresult->provider-list 
    (j/query db ["SELECT provider_id FROM providers"])))

(defn delete-provider
  "Remove a provider from the database completely, including all of its concepts."
  [db provider-id]
  (ct/delete-provider-concept-tables db provider-id) 
  (j/delete! db  :providers ["provider_id = ?" provider-id]))

(defn reset-providers
  "Delete all providers from the database including their concept tables.  USE WITH CAUTION."
  [db]
  (doseq [provider-id (get-providers db)]
    (delete-provider db provider-id)))

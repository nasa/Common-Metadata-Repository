(ns cmr.metadata-db.data.provider
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.utility :as util]))

(defn dbresult->provider-list
  "Convert the sequence of maps coming from the database into a sequence of strings"
  [result]
  (println "---------------------")
  (println result)
  (let [rval (map :provider_id result)]
    (println rval)
    rval))

(defn save-provider
  "Saves a provider and returns the provider id. If the provider already 
  exists then an exception is thrown."
  [db provider-id]
  (try (j/insert! db 
                  :providers
                  ["provider_id"]
                  [provider-id])
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
  (try (let [providers (j/query db ["SELECT provider_id FROM providers"])]
         {:providers (dbresult->provider-list providers)})
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)]
        {:error :unknown-error :error-message error-message})))) 

(defn delete-provider
  "Remove a provider from the database completely, including all of its concepts."
  [db provider-id]
  
  (try (j/delete! db  :providers ["provider_id = ?" provider-id])
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)]
        {:error :unknown-error :error-message error-message}))))

(defn reset-providers
  "Delete all providers from the database including their concept tables.  USE WITH CAUTION."
  [db]
  ;; TODO drop concept tables for all providers
  (try (j/db-do-commands db "DELETE FROM providers")
    {:status :ok}
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)]
        {:error :unknown-error :error-message error-message}))))

(comment
  (save-provider (:db user/system) "PROV1")
  )
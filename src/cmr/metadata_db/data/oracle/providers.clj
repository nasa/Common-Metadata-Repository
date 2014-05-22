(ns cmr.metadata-db.data.oracle.providers
  "Functions for saving, retrieving, deleting providers."
  (:require [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.data.providers :as p]
            [cmr.metadata-db.data.oracle.concept-tables :as ct])
  (:import cmr.oracle.connection.OracleStore))

(defn dbresult->provider-list
  "Convert the sequence of maps coming from the database into a sequence of strings"
  [result]
  (map :provider_id result))

(extend-protocol p/ProvidersStore
  OracleStore

  (save-provider
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

  (get-providers
    [db]
    (dbresult->provider-list
      (j/query db ["SELECT provider_id FROM providers"])))

  (delete-provider
    [db provider-id]
    (try (do
           (ct/delete-provider-concept-tables db provider-id)
           (j/delete! db :providers ["provider_id = ?" provider-id]))
      (catch Exception e
        (error (.getMessage e))
        (let [error-message (.getMessage e)
              error-code (cond
                           (re-find #"table or view does not exist" error-message)
                           :not-found

                           :else
                           :unknown-error)]
          {:error error-code :error-message error-message}))))

  (reset-providers
    [db]
    (doseq [provider-id (p/get-providers db)]
      (p/delete-provider db provider-id))))

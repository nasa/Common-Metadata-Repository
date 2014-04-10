(ns cmr.metadata-db.data.provider
  "Functions for saving, retrieving, deleting providers.")

(save-provider
  "Saves a provider and returns the provider id. If the provider already 
  exists then an exception is thrown."
  [db provider-id]
  [this provider-id]
  (try (j/insert! this 
                  "METADATA_DB.provider"
                  ["provider_id"]
                  [provider-id])
    {:provider-id provider-id}
    (catch Exception e
      (error (.getMessage e))
      (let [error-message (.getMessage e)
            error-code (cond
                         (re-find #"METADATA_DB.UNIQUE_PROVIDER_ID" error-message)
                         :concept-id-concept-conflict
                         
                         :else
                         :unknown-error)]
        {:error error-code :error-message error-message}))))

(get-providers
  "Get a sequence of all the providers."
  [db]
  ) 

(delete-provider
  "Remove a provider from the database completely, including all of its concepts."
  [db provider-id]
  )

(reset-providers
  "Delete all providers from the database.  USE WITH CAUTION."
  [db]
  )

(ns cmr.metadata-db.data.oracle-provider-store
  "Addes ProviderStore methods to OracleStore."
  (:require [cmr.metadata-db.data.oracle :as oracle]
            [cmr.metadata-db.providers :as provider]
            [cmr.common.lifecycle :as lifecycle]
            [clojure.string :as string]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as cutil]
            [clojure.pprint :refer (pprint pp)]
            [clojure.java.jdbc :as j]
            [cmr.metadata-db.services.utility :as util])
  (:import cmr.metadata_db.data.oracle.OracleStore))

;;; Utility methods

(extend-type OracleStore
  
  provider/ProviderStore
  
  (save-provider
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
  
  (delete-provider
    [this provider-id]
    )
  
  
  (get-providers
    [this]
    
    )
  (reset-providers
    [this]
    ))


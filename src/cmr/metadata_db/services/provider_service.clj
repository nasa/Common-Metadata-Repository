(ns cmr.metadata-db.services.provider-service
  (require [cmr.metadata-db.data.providers :as providers]
           [cmr.common.services.errors :as errors]
           [cmr.metadata-db.services.messages :as msg]
           [cmr.common.services.messages :as cmsg]
           [cmr.transmit.echo.rest :as rest]
           [cmr.metadata-db.services.util :as util]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn create-provider
  "Save a provider and setup concept tables in the database."
  [context provider-id]
  (info "Creating provider [" provider-id "]")
  (util/validate-provider-id provider-id)
  (let [db (util/context->db context)
        result (providers/save-provider db provider-id)
        error-code (:error result)]
    (when error-code
      (cond
        (= error-code :provider-id-conflict)
        (cmsg/data-error :conflict
                         msg/provider-exists
                         provider-id)

        :else
        (errors/internal-error! (:error-message result))))
    result))

(deftracefn get-providers
  "Get the list of providers."
  [context]
  (info "Getting provider list.")
  (let [db (util/context->db context)]
    (providers/get-providers db)))

(deftracefn delete-provider
  "Delete a provider and all its concept tables."
  [context provider-id]
  (info "Deleting provider [" provider-id "]")
  (let [db (util/context->db context)
        result (providers/delete-provider db provider-id)
        error-code (:error result)]
    (when error-code
      (cond
        (= error-code :not-found)
        (cmsg/data-error :not-found msg/provider-does-not-exist provider-id)

        :else
        (errors/internal-error! (:error-message result))))))

(deftracefn reset-providers
  "Delete all the providers and their concepts."
  [context]
  (info "Deleting all providers and concepts.")
  (let [db (util/context->db context)]
    (providers/reset-providers db)))

(deftracefn get-db-health
  "Get the health status of db, we do this by getting providers out of db."
  [context]
  (try
    (get-providers context)
    {:ok? true}
    (catch Exception e
      {:ok? false
       :problem (.getMessage e)})))

(deftracefn health
  "Returns the health state of the app."
  [context]
  (let [db-health (get-db-health context)
        echo-rest-health (rest/health context)
        ok? (and (:ok? db-health) (:ok? echo-rest-health))]
    {:ok? ok?
     :dependencies {:oracle db-health
                    :echo echo-rest-health}}))


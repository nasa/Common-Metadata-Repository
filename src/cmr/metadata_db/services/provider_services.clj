(ns cmr.metadata-db.services.provider-services
  (require [cmr.metadata-db.data.oracle.provider :as provider]
           [cmr.common.services.errors :as errors]
           [cmr.metadata-db.services.messages :as messages]
           [cmr.metadata-db.services.utility :as util]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.system-trace.core :refer [deftracefn]]))


(defn validate-provider-id
  "Verify that a provider-id is in the correct format."
  [provider-id]
  (when-let [error-message (cond
                             (> (count provider-id) 10)
                             messages/provider-id-too-long

                             (empty? provider-id)
                             messages/provider-id-empty

                             (not (re-matches #"^[a-zA-Z](\w|_)*" provider-id))
                             messages/invalid-provider-id)]
    (messages/data-error :invalid-data error-message provider-id)))

(deftracefn create-provider
  "Save a provider and setup concept tables in the database."
  [context provider-id]
  (info "Creating provider " provider-id)
  (validate-provider-id provider-id)
  (let [db (util/context->db context)
        result (provider/save-provider db provider-id)
        error-code (:error result)]
    (when error-code
      (cond
        (= error-code :provider-id-conflict)
        (messages/data-error :conflict
                             messages/provider-exists
                             provider-id)

        :else
        (errors/internal-error! (:error-message result))))
    result))

(deftracefn get-providers
  "Get the list of providers."
  [context]
  (info "Getting provider list.")
  (let [db (util/context->db context)]
    (provider/get-providers db)))

(deftracefn delete-provider
  "Delete a provider and all its concept tables."
  [context provider-id]
  (info "Deleting provider " provider-id)
  (let [db (util/context->db context)
        result (provider/delete-provider db provider-id)
        error-code (:error result)]
    (when error-code
      (cond
        (= error-code :not-found)
        (messages/data-error :not-found messages/provider-does-not-exist-msg provider-id)

        :else
        (errors/internal-error! (:error-message result))))))


(deftracefn reset-providers
  "Delete all the providers and their concepts."
  [context]
  (info "Deleting all providers and concepts.")
  (let [db (util/context->db context)]
    (provider/reset-providers db)))

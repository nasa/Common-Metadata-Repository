(ns cmr.metadata-db.services.provider-services
  (require [cmr.metadata-db.data.provider :as provider]
           [cmr.common.services.errors :as errors]
           [cmr.metadata-db.services.messages :as messages]
           [cmr.metadata-db.services.utility :as util]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.system-trace.core :refer [deftracefn]]))


;;; Utility methods

(defn validate-provider-id
  "Verify that a provider-id is in the correct format."
  [provider-id]
  )



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

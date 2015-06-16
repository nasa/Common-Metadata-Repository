(ns cmr.metadata-db.services.provider-service
  (:require [cmr.metadata-db.data.providers :as providers]
            [cmr.metadata-db.services.util :as mdb-util]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.metadata-db.services.provider-validation :as pv]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.util :as util]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

(deftracefn create-provider
  "Save a provider and setup concept tables in the database."
  [context {:keys [provider-id] :as provider}]
  (info "Creating provider [" provider-id "]")
  (pv/validate-provider provider)
  (let [db (mdb-util/context->db context)
        existing-provider (providers/get-provider db provider-id)]
    (if existing-provider
      (cmsg/data-error :conflict msg/provider-exists provider-id)
      (let [result (providers/save-provider db provider)]
        (when (:error result)
          (errors/internal-error! (:error-message result)))))))

(deftracefn get-providers
  "Get the list of providers."
  [context]
  (info "Getting provider list.")
  (let [db (mdb-util/context->db context)]
    (providers/get-providers db)))

(deftracefn update-provider
  "Updates a provider."
  [context {:keys [provider-id small] :as provider}]
  (info "Updating provider [" provider-id "]")
  (pv/validate-provider provider)
  (let [db (mdb-util/context->db context)
        existing-provider (providers/get-provider db provider-id)]
    (if existing-provider
      (if (= small (:small existing-provider))
        (let [result (providers/update-provider db provider)]
          (when (:error result)
            (errors/internal-error! (:error-message result))))
        (cmsg/data-error :bad-request msg/provider-small-field-cannot-be-modified provider-id))
      (cmsg/data-error :not-found msg/provider-does-not-exist provider-id))))

(deftracefn delete-provider
  "Delete a provider and all its concept tables."
  [context provider-id]
  (info "Deleting provider [" provider-id "]")
  (when (= pv/small-provider-id provider-id)
    (cmsg/data-error :bad-request msg/small-provider-cannot-be-deleted))
  (let [db (mdb-util/context->db context)
        provider (providers/get-provider db provider-id)]
    (if provider
      (let [result (providers/delete-provider db provider)]
        (when (:error result)
          (errors/internal-error! (:error-message result))))
      (cmsg/data-error :not-found msg/provider-does-not-exist provider-id))))

(deftracefn reset-providers
  "Delete all the providers and their concepts."
  [context]
  (info "Deleting all providers and concepts.")
  (let [db (mdb-util/context->db context)]
    (providers/reset-providers db)))


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
  [context {:keys [provider-id short-name] :as provider}]
  (info "Creating provider [" provider-id "]")
  (pv/validate-provider provider)
  (let [db (mdb-util/context->db context)
        providers (providers/get-providers db)]
    (when (some #(when (= provider-id (:provider-id %)) %) providers)
      (cmsg/data-error :conflict msg/provider-with-id-exists provider-id))
    (when-let [existing-provider (some #(when (= short-name (:short-name %)) %) providers)]
      (cmsg/data-error :conflict msg/provider-with-short-name-exists existing-provider))
    (let [result (providers/save-provider db provider)]
      (when (:error result)
        (errors/internal-error! (:error-message result))))))

(deftracefn get-providers
  "Get the list of providers."
  [context]
  (info "Getting provider list.")
  (let [db (mdb-util/context->db context)]
    (providers/get-providers db)))

(deftracefn update-provider
  "Updates a provider."
  [context {:keys [provider-id short-name small] :as provider}]
  (info "Updating provider [" provider-id "]")
  (pv/validate-provider provider)
  (let [db (mdb-util/context->db context)
        existing-provider (providers/get-provider db provider-id)]
    (when-not existing-provider
      (cmsg/data-error :not-found msg/provider-does-not-exist provider-id))
    (when-not (= short-name (:short-name existing-provider))
      (cmsg/data-error :bad-request
                       msg/provider-short-name-cannot-be-modified existing-provider short-name))
    (when-not (= small (:small existing-provider))
      (cmsg/data-error :bad-request msg/provider-small-field-cannot-be-modified provider-id))
    (let [result (providers/update-provider db provider)]
      (when (:error result)
        (errors/internal-error! (:error-message result))))))

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


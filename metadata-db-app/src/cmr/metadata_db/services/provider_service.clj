(ns cmr.metadata-db.services.provider-service
  (:require
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.services.messages :as cmsg]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.providers :as providers]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-validation :as pv]
   [cmr.metadata-db.services.util :as mdb-util]
   [cmr.efs.config :as efs-config]))

(defn create-provider
  "Save a provider and setup concept tables in the database."
  ([context {:keys [provider-id short-name] :as provider}]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (create-provider context {:keys [provider-id short-name] :as provider} (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (create-provider context {:keys [provider-id short-name] :as provider} (mdb-util/context->efs-db context))))
  ([context {:keys [provider-id short-name] :as provider} db]
   (info "Creating provider [" provider-id "]")
   (pv/validate-provider provider)
   (let [providers (providers/get-providers db)]
     (when (some #(= provider-id (:provider-id %)) providers)
       (cmsg/data-error :conflict msg/provider-with-id-exists provider-id))
     (when-let [existing-provider (some #(when (= short-name (:short-name %)) %) providers)]
       (cmsg/data-error :conflict msg/provider-with-short-name-exists existing-provider))
     (providers/save-provider db provider))))

(defn get-providers
  "Get the list of providers.
  Returns a clojure.lang.APersistentMap$ValSeq; list of maps"
  ([context]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (get-providers context (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (get-providers context (mdb-util/context->efs-db context))))
  ([context db]
   (info "Getting provider list.")
   (let [providers (providers/get-providers db)]
     (map util/remove-nil-keys providers))))

(defn get-provider-by-id
  "Returns the provider with the given provider-id, raise error when provider does not exist based
  on the throw-error flag"
  ([context provider-id]
   (get-provider-by-id context provider-id false))
  ([context provider-id throw-error?]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (get-provider-by-id context provider-id throw-error? (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (get-provider-by-id context provider-id throw-error? (mdb-util/context->efs-db context))))
  ([context provider-id throw-error? db]
   (or (when (= (:provider-id pv/cmr-provider) provider-id)
         pv/cmr-provider)
       (providers/get-provider db provider-id)
       (when throw-error?
         (errors/throw-service-error :not-found (msg/provider-does-not-exist provider-id))))))

(defn update-provider
  "Updates a provider."
  ([context {:keys [provider-id short-name small] :as provider}]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (update-provider context {:keys [provider-id short-name small] :as provider} (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (update-provider context {:keys [provider-id short-name small] :as provider} (mdb-util/context->efs-db context))))
  ([context {:keys [provider-id short-name small] :as provider} db]
   (info "Updating provider [" provider-id "]")
   (pv/validate-provider provider)
   (let [providers (providers/get-providers db)
         existing-provider (get-provider-by-id context provider-id true)]
     (when-let [conflict-provider (some #(when (and (= short-name (:short-name %))
                                                    (not= provider-id (:provider-id %))) %)
                                        providers)]
       (cmsg/data-error :conflict msg/provider-with-short-name-exists conflict-provider))
     (when-not (= small (:small existing-provider))
       (cmsg/data-error :bad-request msg/provider-small-field-cannot-be-modified provider-id))
     (providers/update-provider db provider))))

(defn delete-provider
  "Delete a provider and all its concept tables."
  ([context provider-id]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (delete-provider context provider-id (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (delete-provider context provider-id (mdb-util/context->efs-db context))))
  ([context provider-id db]
   (info "Deleting provider [" provider-id "]")
   (pv/validate-provider-id-deletion provider-id)
   (let [provider (get-provider-by-id context provider-id true)
         result (providers/delete-provider db provider)]
     (when (:error result)
       (errors/internal-error! (:error-message result))))))

(defn reset-providers
  "Delete all the providers and their concepts."
  ([context]
   (when (or
          (= "efs-off" efs-config/efs-toggle)
          (= "efs-on" efs-config/efs-toggle))
     (reset-providers context (mdb-util/context->db context)))
   (when (or
          (= "efs-on" efs-config/efs-toggle)
          (= "efs-only" efs-config/efs-toggle))
     (reset-providers context (mdb-util/context->efs-db context))))
  ([context db]
   (info "Deleting all providers and concepts.")
   (providers/reset-providers db)))

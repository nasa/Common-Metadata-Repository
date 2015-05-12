(ns cmr.metadata-db.services.provider-service
  (:require [cmr.metadata-db.data.providers :as providers]
            [cmr.metadata-db.services.util :as mdb-util]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.util :as util]
            [cmr.common.validations.core :as v]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))

(def ^:private ^:const PROVIDER_ID_MAX_LENGTH 10)

(defn- provider-id-length-validation
  "Validates the provider id isn't too long."
  [field-path provider-id]
  (when (> (count provider-id) PROVIDER_ID_MAX_LENGTH)
    {field-path [(msg/provider-id-too-long provider-id)]}))

(defn- provider-id-empty-validation
  "Validates the provider id isn't empty."
  [field-path provider-id]
  (when (empty? provider-id)
    {field-path [(msg/provider-id-empty)]}))

(defn- provider-id-format-validation
  "Validates the provider id is in the correct format."
  [field-path provider-id]
  (when (and provider-id (not (re-matches #"^[A-Z][A-Z0-9_]*" provider-id)))
    {field-path [(msg/invalid-provider-id provider-id)]}))

(defn- must-be-boolean
  "Validates the value given is of Boolean type."
  [field-path value]
  (when-not (or (= true value) (= false value))
    {field-path [(format "%%s must be either true or false but was [%s]" (pr-str value))]}))

(def ^:private provider-validations
  {:provider-id (v/first-failing provider-id-length-validation
                                 provider-id-empty-validation
                                 provider-id-format-validation)
   :cmr-only (v/first-failing v/required must-be-boolean)})

(defn validate-provider
  "Validates the provider. Throws an exception with validation errors if the provider is invalid."
  [provider]
  (let [errors (v/validate provider-validations provider)]
    (when (seq errors)
      (errors/throw-service-errors :bad-request (v/create-error-messages errors)))))

(deftracefn create-provider
  "Save a provider and setup concept tables in the database."
  [context {:keys [provider-id] :as provider}]
  (info "Creating provider [" provider-id "]")
  (validate-provider provider)
  (let [db (mdb-util/context->db context)
        result (providers/save-provider db provider)
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
  (let [db (mdb-util/context->db context)]
    (providers/get-providers db)))

(deftracefn update-provider
  "Updates a provider."
  [context {:keys [provider-id] :as provider}]
  (info "Updating provider [" provider-id "]")
  (validate-provider provider)
  (let [db (mdb-util/context->db context)
        result (providers/update-provider db provider)
        error-code (:error result)]
    (when error-code
      (cond
        (= error-code :not-found)
        (cmsg/data-error :not-found msg/provider-does-not-exist provider-id)

        :else
        (errors/internal-error! (:error-message result))))))

(deftracefn delete-provider
  "Delete a provider and all its concept tables."
  [context provider-id]
  (info "Deleting provider [" provider-id "]")
  (let [db (mdb-util/context->db context)
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
  (let [db (mdb-util/context->db context)]
    (providers/reset-providers db)))


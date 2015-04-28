(ns cmr.metadata-db.services.provider-service
  (:require [cmr.metadata-db.data.providers :as providers]
            [cmr.metadata-db.services.util :as mdb-util]
            [cmr.common.services.errors :as errors]
            [cmr.metadata-db.services.messages :as msg]
            [cmr.common.services.messages :as cmsg]
            [cmr.common.util :as util]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.system-trace.core :refer [deftracefn]]))


(defn provider-id-length-validation
  [provider-id]
  (when (> (count provider-id) 10)
    [(msg/provider-id-too-long provider-id)]))

(defn provider-id-empty-validation
  [provider-id]
  (when (empty? provider-id)
    [(msg/provider-id-empty provider-id)]))

(defn provider-id-format-validation
  [provider-id]
  (when provider-id
    (when-not (re-matches #"^[a-zA-Z](\w|_)*" provider-id)
      [(msg/invalid-provider-id provider-id)])))

(def provider-id-validation
  "Verify that a provider-id is in the correct form and return a list of errors if not."
  (util/compose-validations [provider-id-length-validation
                             provider-id-empty-validation
                             provider-id-format-validation]))

(def validate-provider-id
  "Validates a provider-id. Throws an error if invalid."
  (util/build-validator :invalid-data provider-id-validation))

(deftracefn create-provider
  "Save a provider and setup concept tables in the database."
  [context {:keys [provider-id] :as provider}]
  (info "Creating provider [" provider-id "]")
  (validate-provider-id provider-id)
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


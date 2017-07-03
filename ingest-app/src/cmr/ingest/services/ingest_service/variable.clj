(ns cmr.ingest.services.ingest-service.variable
  (:require
   [clojure.edn :as edn]
   [cmr.common.api.context :as context-util]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.validations.core :as cv]
   [cmr.ingest.services.ingest-service.util :as util]
   [cmr.ingest.services.messages :as msg]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]))

(def ^:private update-variable-validations
  "Service level validations when updating a variable."
  [(cv/field-cannot-be-changed :variable-name)
   ;; Originator id cannot change but we allow it if they don't specify a
   ;; value.
   (cv/field-cannot-be-changed :originator-id true)])

(defn validate-update-variable
  "Validates a variable update."
  [existing-variable updated-variable]
  (cv/validate!
   update-variable-validations
   (assoc updated-variable :existing existing-variable)))

(defn overwrite-variable-tombstone
  "This function is called when the variable exists but was previously
  deleted."
  [context concept variable user-id]
  (mdb2/save-concept context
                    (-> concept
                        (assoc :metadata (pr-str variable)
                               :deleted false
                               :user-id user-id)
                        (dissoc :revision-date)
                        (update-in [:revision-id] inc))))

(defn- variable->new-concept
  "Converts a variable into a new concept that can be persisted in metadata
  db."
  [variable]
  {:concept-type :variable
   :native-id (:native-id variable)
   :metadata (pr-str variable)
   :user-id (:originator-id variable)
   ;; The first version of a variable should always be revision id 1. We
   ;; always specify a revision id when saving variables to help avoid
   ;; conflicts
   :revision-id 1
   :format mt/edn
   :extra-fields {:variable-name (:name variable)
                  :measurement (:long-name variable)}})

(defn- fetch-variable-concept
  "Fetches the latest version of a variable concept variable."
  [context provider-id native-id]
  (if-let [concept (mdb/find-latest-concept context
                                            {:provider-id provider-id
                                             :native-id native-id
                                             :latest true}
                                            :variable)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (msg/variable-deleted native-id))
      concept)
    (errors/throw-service-error
     :not-found
     (msg/variable-does-not-exist native-id))))

(defn update-variable
  "Updates an existing variable with the given concept id."
  [context provider-id native-id metadata]
  (let [updated-variable (util/concept-json->concept metadata)
        existing-concept (fetch-variable-concept provider-id native-id)
        existing-variable (edn/read-string (:metadata existing-concept))]
    (validate-update-variable existing-variable updated-variable)
    (mdb/save-concept
      context
      (-> existing-concept
          ;; The updated variable won't change the originator of the existing
          ;; variable
          (assoc :metadata (-> updated-variable
                               (assoc :originator-id
                                      (:originator-id existing-variable))
                               (pr-str))
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-variable-modification))
          (dissoc :revision-date :transaction-id)
          (update-in [:revision-id] inc)))))

(defn delete-variable
  "Deletes a tag with the given concept id"
  [context provider-id native-id]
  (let [existing-concept (fetch-variable-concept context provider-id native-id)]
    (mdb/save-concept
      context
      (-> existing-concept
          ;; Remove fields not allowed when creating a tombstone.
          (dissoc :metadata :format :provider-id :native-id :transaction-id)
          (assoc :deleted true
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-variable-modification))
          (dissoc :revision-date :created-at :extra-fields)
          (update-in [:revision-id] inc)))))

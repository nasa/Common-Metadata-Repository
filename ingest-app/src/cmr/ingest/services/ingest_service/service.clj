(ns cmr.ingest.services.ingest-service.service
  (:require
    [clojure.edn :as edn]
    [clojure.string :as string]
    [cmr.common.api.context :as context-util]
    [cmr.common.mime-types :as mt]
    [cmr.common.services.errors :as errors]
    [cmr.common.util :refer [defn-timed]]
    [cmr.common.validations.core :as cv]
    [cmr.ingest.services.ingest-service.util :as util]
    [cmr.ingest.services.messages :as msg]
    [cmr.transmit.metadata-db :as mdb]
    [cmr.transmit.metadata-db2 :as mdb2]))

(def ^:private update-service-validations
  "Service level validations when updating a service."
  [(cv/field-cannot-be-changed :service-name)
   ;; Originator id cannot change but we allow it if they don't specify a
   ;; value.
   (cv/field-cannot-be-changed :originator-id true)])

(defn validate-update-service
  "Validates a service update."
  [existing-service updated-service]
  (cv/validate!
   update-service-validations
   (assoc updated-service :existing existing-service)))

(defn overwrite-service-tombstone
  "This function is called when the service exists but was previously
  deleted."
  [context concept service user-id]
  (mdb2/save-concept context
                    (-> concept
                        (assoc :metadata (pr-str service)
                               :deleted false
                               :user-id user-id)
                        (dissoc :revision-date)
                        (update-in [:revision-id] inc))))

(defn- service->new-concept
  "Converts a service into a new concept that can be persisted in metadata
  db."
  [service]
  {:concept-type :service
   :native-id (:native-id service)
   :metadata (pr-str service)
   :user-id (:originator-id service)
   ;; The first version of a service should always be revision id 1. We
   ;; always specify a revision id when saving variables to help avoid
   ;; conflicts
   :revision-id 1
   :format mt/edn
   :extra-fields {
     :service-name (:name service)}})

(defn- fetch-service-concept
  "Fetches the latest version of a service concept service service-key."
  [context service-key]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id service-key
                                             :latest true}
                                            :service)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (msg/service-deleted service-key))
      concept)
    (errors/throw-service-error
     :not-found
     (msg/service-does-not-exist service-key))))

(defn-timed create-service
  "Creates the service, saving it as a revision in metadata db. Returns the
  concept id and revision id of the saved service."
  [context service-json-str]
  (let [user-id (context-util/context->user-id
                 context
                 msg/token-required-for-service-modification)
        service (as-> service-json-str data
                      (util/concept-json->concept data)
                      (assoc data :originator-id user-id)
                      (assoc data :native-id (string/lower-case (:name data))))]
    ;; Check if the service already exists
    (if-let [concept-id (mdb2/get-concept-id context
                                             :service
                                             "CMR"
                                             (:native-id service)
                                             false)]
      ;; The service exists. Check if its latest revision is a tombstone
      (let [concept (mdb2/get-latest-concept context concept-id false)]
        (if (:deleted concept)
          (overwrite-service-tombstone context concept service user-id)
          (errors/throw-service-error
           :conflict
           (msg/service-already-exists service concept-id))))
      ;; The service doesn't exist
      (mdb2/save-concept context
       (service->new-concept service)))))

(defn update-service
  "Updates an existing service with the given concept id."
  [context service-key service-json-str]
  (let [updated-service (util/concept-json->concept service-json-str)
        existing-concept (fetch-service-concept context service-key)
        existing-service (edn/read-string (:metadata existing-concept))]
    (validate-update-service existing-service updated-service)
    (mdb/save-concept
      context
      (-> existing-concept
          ;; The updated service won't change the originator of the existing
          ;; service
          (assoc :metadata (-> updated-service
                               (assoc :originator-id
                                      (:originator-id existing-service))
                               (pr-str))
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-service-modification))
          (dissoc :revision-date :transaction-id)
          (update-in [:revision-id] inc)))))

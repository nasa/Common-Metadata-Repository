(ns cmr.ingest.services.ingest-service.service
  (:require
   [clojure.edn :as edn]
   [cmr.common.api.context :as context-util]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [defn-timed]]
   [cmr.common.validations.core :as cv]
   [cmr.ingest.services.ingest-service.util :as util]
   [cmr.ingest.services.messages :as msg]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.transmit.metadata-db2 :as mdb2]
   [cmr.umm-spec.umm-spec-core :as spec]))

(defn add-extra-fields-for-service
  "Returns service concept with fields necessary for ingest into metadata db
  under :extra-fields."
  [context concept service]
  (assoc concept :extra-fields {:service-name (:Name service)}))

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

(defn- fetch-service-concept
  "Fetches the latest version of a service concept service."
  [context provider-id native-id]
  (if-let [concept (mdb/find-latest-concept context
                                            {:provider-id provider-id
                                             :native-id native-id
                                             :latest true}
                                            :service)]
    (if (:deleted concept)
      (errors/throw-service-error
       :not-found
       (msg/service-deleted native-id))
      concept)
    (errors/throw-service-error
     :not-found
     (msg/service-does-not-exist native-id))))

(defn-timed save-service
  "Store a service concept in mdb and indexer. Return name, long-name, concept-id, and
  revision-id."
  [context concept]
  (let [metadata (:metadata concept)
        service (spec/parse-metadata context :service (:format concept) metadata)
        concept (add-extra-fields-for-service context concept service)
        {:keys [concept-id revision-id]} (mdb2/save-concept context
                                          (assoc concept :provider-id (:provider-id concept)
                                                         :native-id (:native-id concept)))]
      {:concept-id concept-id
       :revision-id revision-id}))

(defn delete-service
  "Deletes a tag with the given concept id"
  [context provider-id native-id]
  (let [existing-concept (fetch-service-concept context provider-id native-id)]
    (mdb/save-concept
      context
      (-> existing-concept
          ;; Remove fields not allowed when creating a tombstone.
          (dissoc :metadata :format :provider-id :native-id :transaction-id)
          (assoc :deleted true
                 :user-id (context-util/context->user-id
                           context
                           msg/token-required-for-service-modification))
          (dissoc :revision-date :created-at :extra-fields)
          (update-in [:revision-id] inc)))))

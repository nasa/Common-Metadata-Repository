(ns cmr.metadata-db.data.oracle.concepts.generic-documents
  "Implements multi-method variations for generic documents"
  (:require
   [cmr.common.concepts :as cc]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.metadata-db.data.util :as db-util]
   [cmr.oracle.connection :as oracle]))

(doseq [doseq-concept-type (cc/get-generic-concept-types-array)]
  (defmethod c/db-result->concept-map doseq-concept-type
    [concept-type db _provider-id result]
    (when result
      (let [{:keys [native_id
                    concept_id
                    metadata
                    format
                    revision_id
                    revision_date
                    created_at
                    deleted
                    transaction_id
                    provider_id
                    user_id
                    document_name
                    schema]} result]
        (util/remove-nil-keys {:concept-type concept-type
                               :native-id native_id
                               :concept-id concept_id
                               :provider-id provider_id
                               :metadata (when metadata (util/gzip-blob->string metadata))
                               :format (db-util/db-format->mime-type format)
                               :revision-id (int revision_id)
                               :revision-date (oracle/oracle-timestamp->str-time db revision_date)
                               :created-at (when created_at
                                             (oracle/oracle-timestamp->str-time db created_at))
                               :deleted (not= (int deleted) 0)
                               :transaction-id transaction_id
                               :user-id user_id
                               :extra-fields {:document-name document_name
                                              :schema schema}})))))

(defn- concept->insert-args
  [concept]
  (let [{{:keys [document-name schema]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "document_name" "schema"])
     (concat values [provider-id user-id document-name schema])]))

(doseq [doseq-concept-type (cc/get-generic-concept-types-array)]
  (defmethod c/concept->insert-args [doseq-concept-type false]
    [concept _]
    (concept->insert-args concept)))

(doseq [doseq-concept-type (cc/get-generic-concept-types-array)]
  (defmethod c/concept->insert-args [doseq-concept-type true]
    [concept _]
    (concept->insert-args concept)))

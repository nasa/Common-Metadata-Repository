(ns cmr.metadata-db.data.oracle.concepts.variable
  "Implements multi-method variations for variables"
  (:require
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :variable
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :variable-name] (:variable_name result))
          (assoc-in [:extra-fields :measurement] (:measurement result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider.
;; If we ever associate real providers with variables then we will need to add support
;; for small providers as well.
(defmethod c/concept->insert-args [:variable false]
  [concept _]
  (let [{{:keys [variable-name measurement]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "variable_name" "measurement"])
     (concat values [provider-id user-id variable-name measurement])]))

(defn get-associations-for-variable-tombstone
  "Returns the latest revisions of the variable associations for the given variable."
  [db variable-tombstone]
  (concepts/find-latest-concepts db
                                 {:provider-id (:provider-id variable-tombstone)}
                                 {:concept-type :variable-association
                                  :native-id (:native-id variable-tombstone)}))

(defn cascade-delete-variable-associations
  "Save tombstones for all the variable associations for the given variable."
  [db provider variable-tombstone]
  (let [{:keys [concept-id revision-id]} variable-tombstone
        provider-id (:provider-id provider)
        ;; We need to pull pack the saved concept (tombstone) so we can get the transaction-id.
        saved-tombstone (concepts/get-concept
                         db :variable {:provider-id provider-id :small false}
                         concept-id revision-id)
        transaction-id (:transaction-id saved-tombstone)
        variable-associations (get-associations-for-variable-tombstone db variable-tombstone)
        ;; Remove any associations newer than the variable tombstone.
        variable-associations (filter #(< (:transaction-id %) transaction-id)
                                      variable-associations)
        tombstones (map (fn [concept] (-> concept
                                          (assoc :deleted true :metadata "")
                                          (update :revision-id inc)))
                        variable-associations)]
    (doseq [tombstone tombstones]
      (concepts/save-concept db {:provider-id provider-id :small false} tombstone)
      ;; publish tag-association delete event
      (ingest-events/publish-event
        (:context db)
        (ingest-events/concept-delete-event tombstone)))))

;; CMR-2520 Remove this and the related functions when implementing asynchronous cascade deletes
(defmethod c/after-save :variable
  [db provider variable-tombstone]
  (when (:deleted variable-tombstone)
    ;; Cascade deletion to variable-associations
    (cascade-delete-variable-associations db provider variable-tombstone)))

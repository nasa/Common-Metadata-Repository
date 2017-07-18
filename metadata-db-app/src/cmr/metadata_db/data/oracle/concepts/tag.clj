(ns cmr.metadata-db.data.oracle.concepts.tag
  "Implements multi-method variations for tags"
  (:require
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.ingest-events :as ingest-events]
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :tag
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tag)
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small' provider. If we
;; ever associate real providers with tags then we will need to add support for small providers
;; as well.
(defmethod c/concept->insert-args [:tag false]
  [concept _]
  (let [{user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["user_id"])
     (concat values [user-id])]))

(defn get-tag-associations-for-tag-tombstone
  "Returns the latest revisions of the tag associations for the given tag."
  [db tag-concept-tombstone]
  (let [tag-key (:native-id tag-concept-tombstone)]
    (concepts/find-latest-concepts db {:provider-id "CMR"}
                                   {:concept-type :tag-association :tag-key tag-key})))

(defn cascade-tag-delete-to-tag-associations
  "Save tombstones for all the tag associations for the given tag."
  [db tag-concept-tombstone]
  (let [{:keys [concept-id revision-id]} tag-concept-tombstone
        ;; We need to pull pack the saved concept (tombstone) so we can get the transaction-id.
        saved-tombstone (concepts/get-concept db :tag {:provider-id "CMR" :small false}
                                              concept-id revision-id)
        transaction-id (:transaction-id saved-tombstone)
        tag-associations (get-tag-associations-for-tag-tombstone db tag-concept-tombstone)
        ;; Remove any associations newer than the tag tombstone.
        tag-associations (filter #(< (:transaction-id %) transaction-id) tag-associations)
        tombstones (map (fn [concept] (-> concept
                                          (assoc :deleted true :metadata "")
                                          (update :revision-id inc)))
                        tag-associations)]
    (doseq [tombstone tombstones]
      (concepts/save-concept db {:provider-id "CMR" :small false} tombstone)
      ;; publish tag-association delete event
      (ingest-events/publish-event
        (:context db)
        (ingest-events/concept-delete-event tombstone)))))

;; CMR-2520 Remove this and the related functions when implementing asynchronous cascade deletes
(defmethod c/after-save :tag
  [db provider tag-concept-tombstone]
  (when (:deleted tag-concept-tombstone)
    ;; Cascade deletion to tag-associations
    (cascade-tag-delete-to-tag-associations db tag-concept-tombstone)))

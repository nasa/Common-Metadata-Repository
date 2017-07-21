(ns cmr.metadata-db.data.oracle.concepts.tag
  "Implements multi-method variations for tags"
  (:require
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :tag
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :tag)
          (assoc :user-id (:user_id result))))

;; Only "CMR" provider is supported now which is not considered a 'small'
;; provider. If we ever associate real providers with tags then we will need
;; to add support for small providers as well.
(defmethod c/concept->insert-args [:tag false]
  [concept _]
  (let [{user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["user_id"])
     (concat values [user-id])]))

(defmethod c/cascade-delete-associations :tag
  [db provider tombstone]
  (c/cascade-delete-concept-associations
    db provider tombstone
    {:concept-type :tag-association
     ;; :variable name below refers to the table column
     ;; named VARIABLE_NAME that holds the native-id
     ;; value ... this comment will need to be updated
     ;; once we move to using variable concept id.
     :tag-key (:native-id tombstone)}))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defmethod c/after-save :tag
  [db provider tombstone]
  (when (:deleted tombstone)
    ;; Cascade deletion to tag-associations
    (c/cascade-delete-associations db provider tombstone)))

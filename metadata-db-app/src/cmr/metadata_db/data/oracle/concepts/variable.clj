(ns cmr.metadata-db.data.oracle.concepts.variable
  "Implements multi-method variations for variables"
  (:require
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concepts :as c]))

(defmethod c/db-result->concept-map :variable
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :variable)
          (assoc :provider-id (:provider_id result))
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :variable-name] (:variable_name result))
          (assoc-in [:extra-fields :measurement] (:measurement result))))

;; Only "CMR" provider is supported now which is not considered a 'small'
;; provider. If we ever associate real providers with variables then we will
;; need to add support for small providers as well.
(defmethod c/concept->insert-args [:variable false]
  [concept _]
  (let [{{:keys [variable-name measurement]} :extra-fields
         user-id :user-id
         provider-id :provider-id} concept
        [cols values] (c/concept->common-insert-args concept)]
    [(concat cols ["provider_id" "user_id" "variable_name" "measurement"])
     (concat values [provider-id user-id variable-name measurement])]))

(defmethod c/cascade-delete-associations :variable
  [db provider tombstone]
  (c/cascade-delete-concept-associations
    db provider tombstone
    {:concept-type :variable-association
     ;; :variable name below refers to the table column
     ;; named VARIABLE_NAME that holds the native-id
     ;; value ... this comment will need to be updated
     ;; once we move to using variable concept id.
     :variable-name (:native-id tombstone)}))

;; CMR-2520 Readdress this case when asynchronous cascaded deletes are implemented.
(defmethod c/after-save :variable
  [db provider tombstone]
  (when (:deleted tombstone)
    ;; Cascade deletion to variable-associations
    (c/cascade-delete-associations db provider tombstone)))

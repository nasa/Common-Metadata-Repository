(ns cmr.metadata-db.data.oracle.concepts.tag
  "Implements multi-method variations for tags"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.date-time-parser :as p]
            [clj-time.coerce :as cr]
            [cmr.oracle.connection :as oracle]
            [cmr.metadata-db.data.concepts :as concepts]))

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


(defn get-tag-associations-for-tag
  "Returns the latest revisions of the tag associations for the given tag."
  [db tag-concept]
  (let [tag-key (:native-id tag-concept)]
    (concepts/find-latest-concepts db {:provider-id "CMR"} {:concept-type :tag-association
                                                            :tag-key tag-key})))

(defn cascade-tag-delete-to-tag-associations
  "Save tombstones for all the tag associations for the given tag"
  [db tag-concept]
  (let [tag-associations (get-tag-associations-for-tag db tag-concept)
        tombstones (map (fn [concept] (-> concept
                                          (assoc :deleted true :metadata "")
                                          (update :revision-id inc)))
                     tag-associations)]
    (doseq [tombstone tombstones]
      (concepts/save-concept db {:provider-id "CMR" :small false} tombstone))))

(defmethod c/after-save :tag
  [db provider tag]
  (when (:deleted tag)
    ;; Cascade deletion to tag-associations
    (cascade-tag-delete-to-tag-associations db tag)))

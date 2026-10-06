(ns cmr.metadata-db.data.oracle.concepts.collection
  "Implements multi-method variations for collections"
  (:require
   [clj-time.coerce :as cr]
   [cmr.common.date-time-parser :as p]
   [cmr.common.log :refer [info]]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.metadata-db.data.util :as data-util]
   [cmr.oracle.connection :as oracle]))

(defmethod c/db-result->concept-map :collection
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :collection)
          (assoc :user-id (:user_id result))
          (data-util/metadata->umm-concept db)
          (assoc-in [:extra-fields :short-name] (:short_name result))
          (assoc-in [:extra-fields :version-id] (:version_id result))
          (assoc-in [:extra-fields :entry-id] (:entry_id result))
          (assoc-in [:extra-fields :entry-title] (:entry_title result))
          (assoc-in [:extra-fields :delete-time]
                    (when (:delete_time result)
                      (oracle/oracle-timestamp->str-time db (:delete_time result))))))

(defmethod c/concept->insert-args [:collection false]
  [concept _]
  (let [{{:keys [short-name version-id entry-id entry-title delete-time]} :extra-fields
         user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)
        delete-time (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]
    [(concat cols ["short_name" "version_id" "entry_id" "entry_title" "delete_time" "user_id"])
     (concat values [short-name version-id entry-id entry-title delete-time user-id])]))

(defmethod c/concept->insert-args [:collection true]
  [concept _]
  (let [{:keys [provider-id]} concept
        [cols values] (c/concept->insert-args concept false)]
    [(concat cols ["provider_id"])
     (concat values [provider-id])]))

(defmethod c/post-commit-step :collection
  [db provider concept]
  (info (format "CMR-11560 - INSIDE post-commit-step :collection with provider %s and coll-id %s" provider {:concept-id concept}))
  ;; If this is a tombstone (deletion), run your batch-delete loop
  (when (:deleted concept)
    (info "CMR-11560 - Running batched granule deletion for collection" (:concept-id concept))
    ;; Cascade deletion to real deletes of granules
    (concepts/force-delete-by-params db
                                     provider
                                     {:concept-type :granule
                                      :provider-id (:provider-id concept)
                                      :parent-collection-id (:concept-id concept)})))

;;REMOVED and REPLACED with post-commit-step
;(defmethod c/after-save :collection
;  [db provider coll]
;  (info (format "CMR-11560 - INSIDE after-save :collection with provider %s and coll-id %s" provider {:concept-id coll}))
;  (when (:deleted coll)
;    ;; Cascade deletion to real deletes of granules
;    (concepts/force-delete-by-params db
;                                     provider
;                                     {:concept-type :granule
;                                      :provider-id (:provider-id coll)
;                                      :parent-collection-id (:concept-id coll)})))

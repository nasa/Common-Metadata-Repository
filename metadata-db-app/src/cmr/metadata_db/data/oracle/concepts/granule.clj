(ns cmr.metadata-db.data.oracle.concepts.granule
  "Implements multi-method variations for granules"
  (:require
   [clj-time.coerce :as cr]
   [cmr.common.date-time-parser :as p]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.oracle.connection :as oracle]))

(defmethod c/db-result->concept-map :granule
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :granule)
          (assoc-in [:extra-fields :parent-collection-id] (:parent_collection_id result))
          (assoc-in [:extra-fields :delete-time]
                    (when (:delete_time result)
                      (oracle/oracle-timestamp->str-time db (:delete_time result))))
          ;; The granule_ur column was added after the granule records tables had been populated.
          ;; All ingest going forward will populate the granule_ur, however any existing rows will
          ;; have a null granule_ur. For any granule with a null granule_ur we assume the
          ;; granule_ur is the same as the native_id. This is a safe assumption to make as any
          ;; granule ingested before was ingested via Catalog REST which uses granule ur as the
          ;; native id.
          (assoc-in [:extra-fields :granule-ur] (or (:granule_ur result) (:native_id result)))))

(defmethod c/concept->insert-args [:granule false]
  [concept _]
  (let [{{:keys [parent-collection-id delete-time granule-ur]} :extra-fields} concept
        [cols values] (c/concept->common-insert-args concept)
        delete-time (when delete-time (cr/to-sql-time (p/parse-datetime  delete-time)))]
    [(concat cols ["parent_collection_id" "delete_time" "granule_ur"])
     (concat values [parent-collection-id delete-time granule-ur])]))

(defmethod c/concept->insert-args [:granule true]
  [concept _]
  (let [{:keys [provider-id]} concept
        [cols values] (c/concept->insert-args concept false)]
    [(concat cols ["provider_id"])
     (concat values [provider-id])]))

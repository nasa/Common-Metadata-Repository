(ns cmr.metadata-db.data.oracle.concepts.service
  "Implements multi-method variations for services"
  (:require
   [clj-time.coerce :as cr]
   [cmr.common.date-time-parser :as p]
   [cmr.metadata-db.data.concepts :as concepts]
   [cmr.metadata-db.data.oracle.concepts :as c]
   [cmr.oracle.connection :as oracle]))

(defmethod c/db-result->concept-map :service
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :service)
          (assoc :user-id (:user_id result))
          (assoc-in [:extra-fields :entry-id] (:entry_id result))
          (assoc-in [:extra-fields :entry-title] (:entry_title result))
          (assoc-in [:extra-fields :delete-time]
                    (when (:delete_time result)
                      (oracle/oracle-timestamp->str-time db (:delete_time result))))))

(defmethod c/concept->insert-args [:service false]
  [concept _]
  (let [{{:keys [entry-id entry-title delete-time]} :extra-fields
         user-id :user-id} concept
        [cols values] (c/concept->common-insert-args concept)
        delete-time (when delete-time (cr/to-sql-time (p/parse-datetime delete-time)))]
    [(concat cols ["entry_id" "entry_title" "delete_time" "user_id"])
     (concat values [entry-id entry-title delete-time user-id])]))

(defmethod c/concept->insert-args [:service true]
  [concept _]
  (let [{:keys [provider-id]} concept
        [cols values] (c/concept->insert-args concept false)]
    [(concat cols ["provider_id"])
     (concat values [provider-id])]))

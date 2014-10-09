(ns cmr.metadata-db.data.oracle.concepts.granule
  "Implements multi-method variations for granules"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.date-time-parser :as p]
            [clj-time.coerce :as cr]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]))

(defmethod c/db-result->concept-map :granule
  [concept-type db provider-id result]
  (some-> (c/db-result->concept-map :default db provider-id result)
          (assoc :concept-type :granule)
          (assoc-in [:extra-fields :parent-collection-id] (:parent_collection_id result))
          (assoc-in [:extra-fields :delete-time] (when (:delete_time result) (c/oracle-timestamp-tz->clj-time db (:delete_time result))))))

(defmethod c/concept->insert-args :granule
  [concept]
  (let [{{:keys [parent-collection-id delete-time]} :extra-fields} concept
        [cols values] (c/concept->insert-args (assoc concept :concept-type :default))
        delete-time (when delete-time (cr/to-sql-time (p/parse-datetime  delete-time)))]
    [(concat cols ["parent_collection_id" "delete_time"])
     (concat values [parent-collection-id delete-time])]))

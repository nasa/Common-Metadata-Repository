(ns cmr.metadata-db.data.oracle.concepts.granule
  "Implements multi-method variations for granules"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]))

(defmethod c/db-result->concept-map :granule
  [concept-type provider-id result]
  (some-> (c/db-result->concept-map :default provider-id result)
          (assoc :concept-type :granule)
          (assoc-in [:extra-fields :parent-collection-id] (:parent_collection_id result))))

(defmethod c/concept->insert-args :granule
  [concept]
  (let [[cols values] (c/concept->insert-args (assoc concept :concept-type :default))]
    [(conj cols "parent_collection_id")
     (conj values (get-in concept [:extra-fields :parent-collection-id]))]))



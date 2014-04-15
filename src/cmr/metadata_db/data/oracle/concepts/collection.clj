(ns cmr.metadata-db.data.oracle.concepts.collection
  "Implements multi-method variations for collections"
  (:require [cmr.metadata-db.data.oracle.concepts :as c]
            [cmr.metadata-db.data.oracle.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :refer [select from where with order-by desc delete as]]
            [cmr.metadata-db.data.oracle.sql-utils :as su]))

(defmethod c/db-result->concept-map :collection
  [concept-type provider-id result]
  (some-> (c/db-result->concept-map :default provider-id result)
          (assoc :concept-type :collection)
          (assoc-in [:extra-fields :short-name] (:short_name result))
          (assoc-in [:extra-fields :version-id] (:version_id result))
          (assoc-in [:extra-fields :entry-title] (:entry_title result))))

(defmethod c/concept->insert-args :collection
  [concept]
  (let [{{:keys [short-name version-id entry-title]} :extra-fields} concept
        [cols values] (c/concept->insert-args (assoc concept :concept-type :default))]
    [(concat cols ["short_name" "version_id" "entry_title"])
     (concat values [short-name version-id entry-title])]))



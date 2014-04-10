(ns cmr.metadata-db.data.collection
  (:require [cmr.metadata-db.data :as data]
            [cmr.metadata-db.data.concept-tables :as tables]
            [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]))





(defmethod data/get-concept-id :collection
  [db concept-type provider-id native-id]
  ; (let [table (concept-tables)])

  (first (j/query db ["SELECT concept_id
                        FROM METADATA_DB.concept
                        WHERE concept_type = ?
                        AND provider_id = ?
                        AND native_id = ?
                        AND ROWNUM = 1"
                        concept-type
                        provider-id
                        native-id])))
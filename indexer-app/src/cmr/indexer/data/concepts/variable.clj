(ns cmr.indexer.data.concepts.variable
  "Contains functions to parse and convert variable concepts"
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :variable
  [context concept parsed-concept]
  (let [{:keys [concept-id deleted]} concept
        {:keys [variable-name description originator-id]} parsed-concept]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted variables would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :deleted deleted}
      {:concept-id concept-id
       :variable-name.lowercase (string/lower-case variable-name)
       :description description
       :originator-id.lowercase  (util/safe-lowercase originator-id)})))

(defn variable-association->variable-fields
  "Returns the extra-fields of the variable for the given variable association."
  [context variable-association]
  (let [{:keys [variable-name]} variable-association
        native-id (string/lower-case variable-name)
        variable-concept (mdb/find-latest-concept
                          context
                          {:native-id native-id
                           :latest true}
                          :variable)]
    (when-not (:deleted variable-concept)
      (:extra-fields variable-concept))))

(defn variable-associations->elastic-doc
  "Converts the variable association into the portion going in the collection elastic document."
  [context variable-associations]
  (let [variable-fields (map #(variable-association->variable-fields context %)
                             variable-associations)
        variable-names (map :variable-name variable-fields)
        measurements (map :measurement variable-fields)]
    {:variable-names variable-names
     :variable-names.lowercase (map string/lower-case variable-names)
     :measurements measurements
     :measurements.lowercase (map string/lower-case measurements)}))

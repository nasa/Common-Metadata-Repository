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
       :variable-name variable-name
       :variable-name.lowercase (string/lower-case variable-name)
       :description description
       :originator-id.lowercase  (util/safe-lowercase originator-id)})))

(defn- variable-association->variable-concept
  "Returns the variable concept and variable association for the given variable association."
  [context variable-association]
  (let [{:keys [variable-name]} variable-association
        native-id variable-name
        variable-concept (mdb/find-latest-concept
                          context
                          {:native-id native-id
                           :latest true}
                          :variable)]
    (when-not (:deleted variable-concept)
      ;; associate variable association into variable concept, so we can use it to generate
      ;; elastic document for nested variables field
      (assoc variable-concept :variable-association variable-association))))

(defn- variable-concept->elastic-doc
  "Converts the augmented variable concept into the portion going in the collection elastic document."
  [variable-concept]
  (let [{:keys [variable-association]} variable-concept
        measurement (get-in variable-concept [:extra-fields :measurement])
        {:keys [variable-name originator-id data]} variable-association]
    {:measurement measurement
     :measurement.lowercase (string/lower-case measurement)
     :variable variable-name
     :variable.lowercase (string/lower-case variable-name)
     :originator-id.lowercase  (util/safe-lowercase originator-id)}))

(defn variable-associations->elastic-doc
  "Converts the variable association into the portion going in the collection elastic document."
  [context variable-associations]
  (let [variable-concepts (remove nil?
                                  (map #(variable-association->variable-concept context %)
                                       variable-associations))
        variable-fields (map :extra-fields variable-concepts)
        variable-names (map :variable-name variable-fields)
        measurements (map :measurement variable-fields)]
    {:variable-names variable-names
     :variable-names.lowercase (map string/lower-case variable-names)
     :measurements measurements
     :measurements.lowercase (map string/lower-case measurements)
     :variables (map variable-concept->elastic-doc variable-concepts)}))

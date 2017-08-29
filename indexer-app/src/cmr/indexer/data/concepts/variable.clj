(ns cmr.indexer.data.concepts.variable
  "Contains functions to parse and convert variable concepts"
  (:require
   [clojure.string :as string]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.concepts.science-keyword-util :as science-keyword-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :variable
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields variable-associations]} concept
        {:keys [variable-name measurement]} extra-fields
        science-keywords (mapcat science-keyword-util/science-keyword->keywords
                                 (:ScienceKeywords parsed-concept))
        ;; keyword values that are used to index the keyword field
        keyword-values (flatten (conj [variable-name measurement]
                                      science-keywords))]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted variables would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :revision-id revision-id
       :deleted deleted}
      {:concept-id concept-id
       :revision-id revision-id
       :deleted deleted
       :variable-name variable-name
       :variable-name.lowercase (string/lower-case variable-name)
       :measurement measurement
       :measurement.lowercase (string/lower-case measurement)
       :provider-id provider-id
       :provider-id.lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id.lowercase (string/lower-case native-id)
       :keyword (keyword-util/field-values->keyword-text keyword-values)
       :user-id user-id
       :revision-date revision-date
       :metadata-format (name (mt/format-key format))
       ;; associated collections saved in elasticsearch for retrieving purpose in the format of:
       ;; [{"concept_id":"C1200000007-PROV1"}, {"concept_id":"C1200000008-PROV1","revision_id":5}]
       :collections-gzip-b64 (when (seq variable-associations)
                               (util/string->gzip-base64
                                (pr-str
                                 (map (fn [va]
                                        (util/remove-nil-keys
                                         {"concept-id" (:associated-concept-id va)
                                          "revision-id" (:associated-revision-id va)}))
                                      variable-associations))))})))

(defn- variable-association->variable-concept
  "Returns the variable concept and variable association for the given variable association."
  [context variable-association]
  (let [{:keys [variable-concept-id]} variable-association
        variable-concept (mdb/find-latest-concept
                          context
                          {:concept-id variable-concept-id
                           :latest true}
                          :variable)]
    (when-not (:deleted variable-concept)
      ;; associate variable association into variable concept, so we can use it to generate
      ;; elastic document for nested variables field
      (assoc variable-concept :variable-association variable-association))))

(defn- variable-concept->elastic-doc
  "Converts the augmented variable concept into the portion going in the collection elastic document."
  [variable-concept]
  (let [{:keys [variable-association extra-fields]} variable-concept
        {:keys [variable-name measurement]} extra-fields
        {:keys [originator-id data]} variable-association]
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
        variable-native-ids (map :native-id variable-concepts)
        variable-fields (map :extra-fields variable-concepts)
        variable-names (map :variable-name variable-fields)
        measurements (map :measurement variable-fields)]
    {:variable-names variable-names
     :variable-names.lowercase (map string/lower-case variable-names)
     :variable-native-ids variable-native-ids
     :variable-native-ids.lowercase (map string/lower-case variable-native-ids)
     :measurements measurements
     :measurements.lowercase (map string/lower-case measurements)
     :variables (map variable-concept->elastic-doc variable-concepts)}))

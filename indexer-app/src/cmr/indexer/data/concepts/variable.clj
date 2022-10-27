(ns cmr.indexer.data.concepts.variable
  "Contains functions to parse and convert variable concepts"
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.association-util :as assoc-util]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defn- measurement-quantity->elastic-doc
  "Returns the elastic document for the given measurement quantity"
  [quantity]
  (let [value (:Value quantity)]
    {:quantity value
     :quantity-lowercase (util/safe-lowercase value)}))

(defn- measurement-identifier->elastic-doc
  "Converts a measurement identifier into the portion going in an elastic document for indexing."
  [measurement-identifier]
  (let [{context-medium :MeasurementContextMedium
         object :MeasurementObject
         quantities :MeasurementQuantities} measurement-identifier
        base-doc {:contextmedium context-medium
                  :contextmedium-lowercase (util/safe-lowercase context-medium)
                  :object object
                  :object-lowercase (util/safe-lowercase object)}]
    (if (seq quantities)
      (map #(merge base-doc (measurement-quantity->elastic-doc %)) quantities)
      [base-doc])))

(defmethod es/parsed-concept->elastic-doc :variable
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields variable-associations generic-associations]} concept
        {:keys [variable-name measurement]} extra-fields
        concept-seq-id (:sequence-number (concepts/parse-concept-id concept-id))
        schema-keys [:ScienceKeywords :measurement :variable-name :variable-associations :set-names]
        keyword-values (keyword-util/concept-keys->keyword-text
                        (merge parsed-concept extra-fields
                               {:variable-associations (map :associated-concept-id variable-associations)
                                :set-names (map :Name (:Sets parsed-concept))})
                        schema-keys)
        all-assocs (concat variable-associations generic-associations)]
    (if deleted
      ;; This is only called by re-indexing (bulk indexing)
      ;; Regular deleted variables would have gone through the index-service/delete-concept path.
      {:concept-id concept-id
       :revision-id revision-id
       :concept-seq-id (min es/MAX_INT concept-seq-id)
       :concept-seq-id-long concept-seq-id
       :deleted deleted
       :variable-name variable-name
       :variable-name-lowercase (string/lower-case variable-name)
       :measurement measurement
       :measurement-lowercase (string/lower-case measurement)
       :provider-id provider-id
       :provider-id-lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id-lowercase (string/lower-case native-id)
       :keyword keyword-values
       :user-id user-id
       :revision-date revision-date}
      {:concept-id concept-id
       :revision-id revision-id
       :concept-seq-id (min es/MAX_INT concept-seq-id)
       :concept-seq-id-long concept-seq-id
       :deleted deleted
       :variable-name variable-name
       :variable-name-lowercase (string/lower-case variable-name)
       :measurement measurement
       :measurement-lowercase (string/lower-case measurement)
       :provider-id provider-id
       :provider-id-lowercase (string/lower-case provider-id)
       :native-id native-id
       :native-id-lowercase (string/lower-case native-id)
       :keyword keyword-values
       :user-id user-id
       :revision-date revision-date
       :metadata-format (name (mt/format-key format))
       :measurement-identifiers (mapcat measurement-identifier->elastic-doc
                                        (:MeasurementIdentifiers parsed-concept))
       ;; associated collections and generic concepts saved in elasticsearch for retrieving purpose in the format of:
       ;; [{"concept_id":"C1200000007-PROV1"}, {"concept_id":"C1200000008-PROV1","revision_id":5}]
       :associations-gzip-b64 (assoc-util/associations->gzip-base64-str all-assocs concept-id)}))) 

(defn- variable-associations->variable-concepts
  "Returns the variable concepts for the given variable associations."
  [context variable-associations]
  (let [variable-concept-ids (map :variable-concept-id variable-associations)
        id->association (zipmap variable-concept-ids variable-associations)
        add-association-fn (fn [concept]
                             (assoc concept :variable-association (get id->association (:concept-id concept))))
        variable-concepts (mdb/get-latest-concepts context variable-concept-ids true)]
    (->> variable-concepts
         (filter #(not (:deleted %)))
         (map add-association-fn))))

(defn- variable-concept->elastic-doc
  "Converts the augmented variable concept into the portion going in the collection elastic document."
  [variable-concept]
  (let [{:keys [variable-association extra-fields]} variable-concept
        {:keys [variable-name measurement]} extra-fields
        {:keys [originator-id data]} variable-association]
    {:measurement measurement
     :measurement-lowercase (string/lower-case measurement)
     :variable variable-name
     :variable-lowercase (string/lower-case variable-name)
     :originator-id-lowercase  (util/safe-lowercase originator-id)}))

(defn variable-associations->elastic-doc
  "Converts the variable association into the portion going in the collection elastic document."
  [context variable-associations]
  (let [variable-concepts (variable-associations->variable-concepts context variable-associations)
        variable-native-ids (map :native-id variable-concepts)
        variable-concept-ids (map :concept-id variable-concepts)
        variable-fields (map :extra-fields variable-concepts)
        variable-names (map :variable-name variable-fields)
        measurements (map :measurement variable-fields)]
    {:has-variables (some? (seq variable-concepts))
     :variable-names variable-names
     :variable-names-lowercase (map string/lower-case variable-names)
     :variable-concept-ids variable-concept-ids
     :variable-native-ids variable-native-ids
     :variable-native-ids-lowercase (map string/lower-case variable-native-ids)
     :measurements measurements
     :measurements-lowercase (map string/lower-case measurements)
     :variables (map variable-concept->elastic-doc variable-concepts)}))

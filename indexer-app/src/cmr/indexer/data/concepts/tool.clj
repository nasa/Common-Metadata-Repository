(ns cmr.indexer.data.concepts.tool
  "Contains functions to parse and convert tool and tool association concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.concepts.association-util :as assoc-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :tool
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields tool-associations generic-associations]} concept
        {:keys [tool-name]} extra-fields
        long-name (:LongName parsed-concept)
        tool-type (:Type parsed-concept)
        schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :URL
                     :RelatedURLs
                     :ToolKeywords
                     :Organizations]
        keyword-values (keyword-util/concept-keys->keyword-text
                        parsed-concept schema-keys)
        all-assocs (concat tool-associations generic-associations)
        doc-for-deleted
         {:concept-id concept-id
          :revision-id revision-id
          :deleted deleted
          :tool-name tool-name
          :tool-name-lowercase (string/lower-case tool-name)
          :provider-id provider-id
          :provider-id-lowercase (string/lower-case provider-id)
          :native-id native-id
          :native-id-lowercase (string/lower-case native-id)
          :keyword keyword-values
          :user-id user-id
          :revision-date revision-date}]
    (if deleted
      doc-for-deleted
      (assoc doc-for-deleted :metadata-format (name (mt/format-key format))
                             :tool-type-lowercase (string/lower-case tool-type)
                             :long-name long-name
                             :long-name-lowercase (string/lower-case long-name)
                             :associations-gzip-b64 (assoc-util/associations->gzip-base64-str
                                                     all-assocs
                                                     concept-id)))))

(defn- tool-associations->tool-concepts
  "Returns the tool concepts for the given tool associations."
  [context tool-associations]
  (let [tool-concept-ids (map :tool-concept-id tool-associations)
        tool-concepts (mdb/get-latest-concepts context tool-concept-ids true)]
    (remove :deleted tool-concepts)))

(defn- has-formats?
  "Returns true if the given tool has more than one supported formats value.
  i.e. output-formats is not empty and the combination of inputs-formats and
  output-formats contains more than one distinctive value."
  [context tool-concept]
  (let [tool (concept-parser/parse-concept context tool-concept)
        input-formats (:SupportedInputFormats tool)
        output-formats (:SupportedOutputFormats tool)
        distinct-input-output (distinct (concat input-formats output-formats))]
    (and (not (zero? (count output-formats)))
         (> (count distinct-input-output) 1))))

(defn- get-tool-type
  "Get the tool type from the tool metadata that exists in the tool-concept so that it can be indexed with the
   collection when associating a tool with a collection."
  [context tool-concept]
  (:Type (concept-parser/parse-concept context tool-concept)))

(defn tool-associations->elastic-doc
  "Converts the tool association into the portion going in the collection elastic document."
  [context tool-associations]
  (let [tool-concepts (tool-associations->tool-concepts context tool-associations)
        tool-names (map #(get-in % [:extra-fields :tool-name]) tool-concepts)
        tool-types (map #(get-tool-type context %) tool-concepts)
        tool-concept-ids (map :concept-id tool-concepts)
        has-formats (boolean (some #(has-formats? context %) tool-concepts))]
    {:tool-names tool-names
     :tool-names-lowercase (map string/lower-case tool-names)
     :tool-types-lowercase (map string/lower-case tool-types)
     :tool-concept-ids tool-concept-ids
     :has-formats has-formats}))

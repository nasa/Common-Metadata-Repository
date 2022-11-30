(ns cmr.indexer.data.concepts.generic
  "Contains functions to parse and convert Generic Documents (that is a document
   complying to a schema supported by the Generic Document system) to and object
   that can be indexed in lucine."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.util :as util]
   [cmr.indexer.data.concepts.association-util :as assoc-util]
   [cmr.indexer.data.concept-parser :as c-parser]
   [cmr.indexer.data.concepts.generic-util :as gen-util]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as esearch]
   [cmr.transmit.metadata-db :as meta-db]))

(defmulti field->index
  "Functions which convert a part of metadata to a name-value which can be added
   to an index document. This defmulti is directed by looking for an :Indexer
   value in settings and assuming :default if it is not set.
   Usage:
   settings - json configuration for one field
   data - metadata document
   (field->index
            {:Name 'complex-field'
             :Field '.l1'
             :Indexer 'complex-field'
             :Configuration {:sub-fields [:s2] :format '%s == %s'}}
            {:l1 {:s1 'one' :s2 'two'}})"
  (fn [settings _] (or (:Indexer settings) :default)))

(defmethod field->index "complex-field"
  ;; This is an example of a complex indexer which takes a list of sub fields and
  ;; combines them into one field"
  [settings data]
  (let [field-list (get settings :Field ".")
        field-data (get-in data (gen-util/jq->list field-list keyword) {})
        config (get settings :Configuration {})
        sub-fields (get config :sub-fields {})
        layout (get config :format "%s=%s")
        field-name (util/safe-lowercase (:Name settings))
        field-name-lower (str field-name "-lowercase")
        field-value (reduce (fn [data-str, key-name]
                              (str
                               data-str
                               (when-not (empty? data-str) ", ")
                               (format layout key-name (get field-data (keyword key-name)))))
                            ""
                            sub-fields)
        field-value-lower (util/safe-lowercase field-value)]
    {(keyword field-name) field-value
     (keyword field-name-lower) field-value-lower}))

(defmethod field->index :default
  ;; The default indexer which will map one metadata field to two indexes. One is
  ;; with the literal case, another is all lower case"
  [settings data]
  (let [field-name (util/safe-lowercase (:Name settings))
        field-name-lower (str field-name "-lowercase")
        value (get-in data (gen-util/jq->list (:Field settings) keyword))
        value-lower (util/safe-lowercase value)]
    {(keyword field-name) value
     (keyword field-name-lower) value-lower}))

(defn- parsed-concept->elastic-doc
  "Generate elastic document"
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id user-id
                revision-date extra-fields native-id]} concept
        parsed-concept (if (:deleted concept)
                         (c-parser/parse-concept context concept)
                         parsed-concept)
        generic-associations (esearch/parse-non-tombstone-associations
                              context
                              (meta-db/get-generic-associations-for-concept context concept))
        gen-name (csk/->kebab-case (get-in parsed-concept [:MetadataSpecification :Name] ""))
        gen-ver (get-in parsed-concept [:MetadataSpecification :Version])
        index-data-file (format "schemas/%s/v%s/index.json" gen-name gen-ver)
        index-file-raw (slurp (io/resource index-data-file))
        index-data (json/parse-string index-file-raw true)
        schema-keys [:LongName
                     :Version
                     :Description
                     :RelatedURLs]
        keyword-values (keyword-util/concept-keys->keyword-text
                        parsed-concept schema-keys)
        common-doc ;; fields common to all generic documents
        {:concept-id concept-id
         :revision-id revision-id
         :deleted deleted
         :gen-name gen-name
         :gen-name-lowercase (util/safe-lowercase gen-name)
         :gen-version gen-ver
         :generic-type (str gen-name " " gen-ver)
         :provider-id provider-id
         :provider-id-lowercase (util/safe-lowercase provider-id)
         :keyword keyword-values
         :user-id user-id
         :revision-date revision-date
         :native-id native-id
         :native-id-lowercase (string/lower-case native-id)
         :associations-gzip-b64 (assoc-util/associations->gzip-base64-str generic-associations concept-id)}
        configs (gen-util/only-elastic-preferences (:Indexes index-data))
        ;; now add the configured indexes
        doc (reduce
             (fn [data, config] (into data (field->index config parsed-concept)))
             common-doc
             configs)]
    doc))

(doseq [concept-type (concepts/get-generic-concept-types-array)]
  (defmethod esearch/parsed-concept->elastic-doc concept-type
    ;; Public function called by the indexer framework when a document is needed.
    [context concept parsed-concept]
    (parsed-concept->elastic-doc context concept parsed-concept)))

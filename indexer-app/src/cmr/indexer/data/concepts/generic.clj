(ns cmr.indexer.data.concepts.generic
  "Contains functions to parse and convert Generic Documents (that is a document
   complying to a schema supported by the Generic Document system) to and object
   that can be indexed in lucine."
  (:require
    [cmr.common.log :as log :refer [info warn error]]
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.generics :as generics]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util]
   [cmr.common-app.config :as app-config]
   [cmr.indexer.data.concepts.association-util :as assoc-util]
   [cmr.indexer.data.concept-parser :as c-parser]
   [cmr.indexer.data.concepts.generic-util :as gen-util]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as esearch]
   [cmr.transmit.metadata-db :as meta-db]))

(defn- field->index-complex-field
  "This is an example of a complex indexer which takes a list of sub fields and
  combines them into one field"
  [settings data]
  (let [field-list (get settings :Field ".")
        field-data (get-in data (generics/jq->list field-list keyword) {})
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

(defn- field->index-complex-field-with-values-only
  "Complex indexer field that handles both single objects and arrays,
   and formats using field values only (not field names)"
  [settings data]
  (let [field-list (get settings :Field ".")
        field-data (get-in data (generics/jq->list field-list keyword) {})
        config (get settings :Configuration {})
        sub-fields (get config :sub-fields {})
        layout (get config :format "%s=%s")
        field-name (util/safe-lowercase (:Name settings))
        field-name-lower (str field-name "-lowercase")
        field-value (if (vector? field-data)
                      ;; Handle array case
                      (mapv (fn [element]
                              (let [values (map #(get element (keyword %)) sub-fields)]
                                (apply format layout values)))
                            field-data)
                      ;; Handle single object case
                      (let [values (map #(get field-data (keyword %)) sub-fields)]
                        (apply format layout values)))

        field-value-lower (if (vector? field-value)
                            (mapv #(util/safe-lowercase %) field-value)
                            (util/safe-lowercase field-value))]
    {(keyword field-name) field-value
     (keyword field-name-lower) field-value-lower}))

(defn- field->index-simple-array-field
  "The gets the a sub field of an array element and puts those values into a list so that each value
   can be searched on one of two indexes: the literal case, another is all lower case.
   Example usage:
   (field->index-simple-array-field
     ;; settings
     {:Field \".ConceptIds\"
      :Name \"Concept-Ids\"
      :Configuration {:sub-fields [\"Value\", \"ShortName\"]}}
     ;; data
     {:ConceptIds [{:Value \"V-One\" :ShortName \"Short-One\" :ignore \"I-one\"}
                   {:Value \"V-Two\" :ShortName \"Short-Two\" :ignore \"I-two\"}]})
   Result:
   {:concept-ids \"Short-Two Short-One V-Two V-One\"
    :concept-ids-lowercase \"short-two short-one v-two v-one\"}
   "
  [settings data]
  (let [field-list (get settings :Field ".")
        field-data (get-in data (generics/jq->list field-list keyword) {})
        field-name (util/safe-lowercase (:Name settings))
        field-name-lower (str field-name "-lowercase")
        config (get settings :Configuration {})
        sub-fields (get config :sub-fields {})
        value-raw (reduce (fn [data, key-name]
                            (into data (map (keyword key-name) field-data)))
                          (sequence nil)
                          sub-fields)
        value (cond
                (string? value-raw) value-raw
                (coll? value-raw) (clojure.string/join " " (map str value-raw))
                :else (str value-raw))
        value-lower (util/safe-lowercase value)]
    {(keyword field-name) value
     (keyword field-name-lower) value-lower}))

(defn- field->index-default-field
  "The default indexer which will map one metadata field to two indexes. One is with the literal
   case, another is all lower case. If no field is found, then don't return anything
   Example Usage:
   (field->index-default-field
     {:Field \".VisualizationType\" :Name \"VisualizationType\"}
     {:VisualizationType \"Default\"})
   Result:
   {:visualizationtype \"Default\", :visualizationtype-lowercase \"default\"}
   "
  [settings data]
  (let [field-name (util/safe-lowercase (:Name settings))
        field-name-lower (str field-name "-lowercase")
        value (get-in data (generics/jq->list (:Field settings) keyword))
        value-lower (util/safe-lowercase value)]
    (when value
      {(keyword field-name) value
       (keyword field-name-lower) value-lower})))

(defn- field->index
  "Functions which convert a part of metadata to a name-value which can be added
  to an index document. This function is directed by looking for an :Indexer
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
  [settings data]
  (case (:Indexer settings)
    "complex-field" (field->index-complex-field settings data)
    "complex-fields-only" (field->index-complex-field-with-values-only settings data)
    "simple-array-field" (field->index-simple-array-field settings data)
    (field->index-default-field settings data)))

(defn- merge-or-concat
  "Facilitate the behavior of the :AllowAppending configuration by concatinating two values"
  ([existing new-value]
   (merge-or-concat existing new-value " "))
  ([existing new-value separator]
     (string/trim (if (nil? existing)
                    (str new-value)
                    (if (or (empty? existing) (empty? new-value))
                      (str existing new-value)
                      (str existing separator new-value))))))

(defn- post-process-keyword
  "Go through the :keyword and :keyword-lowercase fields in the Elastic Document and make sure they
   are tokenized and sorted in the 'CMR' way."
  [doc]
  (-> doc
      (assoc :keyword
             (keyword-util/field-values->keyword-text [(:keyword doc)]))
      (assoc :keyword-lowercase
             (keyword-util/field-values->keyword-text [(:keyword-lowercase doc)]))))

(defn- release-number
  "Format the software release number for using in the index document. Use a date if in development."
  []
  (let [rel-ver (app-config/release-version)]
    (if (= rel-ver "dev")
      (format "%s-%s", rel-ver, (time-keeper/now))
      (app-config/release-version))))

(defn- parsed-concept->elastic-doc
  "Generate elastic document"
  ([context concept parsed-concept]
   ;; Abstract away the need to have context or find files in the jar with this form of the function
   ;; so that the next interface can be called in tests.
   (let [parsed-concept (if (:deleted concept)
                          (c-parser/parse-concept context concept)
                          parsed-concept)
         generic-associations (esearch/parse-non-tombstone-associations
                               context
                               (meta-db/get-generic-associations-for-concept context concept))
         gen-name (csk/->kebab-case (get-in parsed-concept [:MetadataSpecification :Name] ""))
         gen-ver (get-in parsed-concept [:MetadataSpecification :Version])
         index-data (-> "schemas/%s/v%s/config.json"
                        (format gen-name gen-ver)
                        (io/resource)
                        (slurp)
                        (json/parse-string true))]
     (parsed-concept->elastic-doc concept
                                  parsed-concept
                                  generic-associations
                                  gen-name
                                  gen-ver
                                  index-data)))

  ;; Creating a new overload of the function so that the core functionality can be tested without a
  ;; context
  ([concept parsed-concept generic-associations gen-name gen-ver index-data]
   (let [{:keys [concept-id revision-id deleted provider-id user-id
                 revision-date native-id]} concept
         allow-appending (get-in index-data [:IndexConfiguration :AllowAppending])
         additional-keywords (get-in index-data [:IndexConfiguration :AdditionalKeywords] {})
         ;; Allow Generic Document Configurations to extend the list of simple top level fields that
         ;; can be added to the keyword field. This process does not preclude the use of more
         ;; complicated fields also being added when using the "AllowAppending" setting.
         schema-keys (into [:LongName
                            :Version
                            :Description
                            :RelatedURLs]
                           (map keyword additional-keywords))
         keyword-values (keyword-util/concept-keys->keyword-text
                         parsed-concept schema-keys)
         common-doc ;; fields common to all generic documents
         {:cmr-version (release-number)
          :concept-id concept-id
          :revision-id revision-id
          :deleted deleted
          :gen-name gen-name
          :gen-name-lowercase (util/safe-lowercase gen-name)
          :gen-version gen-ver
          :generic-type (str gen-name " " gen-ver)
          :provider-id provider-id
          :provider-id-lowercase (util/safe-lowercase provider-id)
          :keyword keyword-values
          :keyword-lowercase keyword-values
          :user-id user-id
          :revision-date revision-date
          :native-id native-id
          :native-id-lowercase (string/lower-case native-id)
          :associations-gzip-b64 (assoc-util/associations->gzip-base64-str
                                  generic-associations concept-id)}
         configs (gen-util/only-elastic-preferences (:Indexes index-data))
         ;; now add the configured indexes
         doc (reduce
              (fn [data config]
                ;; Allow Appending from the configuration file allows for multiple rules to be
                ;; defined using the same :Name. When this is set, then these indexes are added
                ;; together to form one larger value made up of different parts of the Generic
                ;; document. Otherwise, the legacy behavier is that the last rule 'wins'.
                (if allow-appending
                  (merge-with merge-or-concat data (field->index config parsed-concept))
                  (into data (field->index config parsed-concept))))
              common-doc
              configs)]
     ;; Finally clean up the keyword indexs which may have been appended
     (post-process-keyword doc))))

(defn field->keyword-fields
  "Gets the name of the fields that are used for keyword searches.
  Returns either nil or a vector of fields."
  [setting]
  (case (:Indexer setting)
    "complex-field" nil
    ;; this is for both simple-field-array and the default
    (when-let [field (:Name setting)]
      [(util/safe-lowercase field)
       (str field "-lowercase")])))

(defn- get-draft-keywords
  "Get the list of keywords for draft records."
  [configs doc]
  (let [sub-fields (reduce (fn [total-fields, config]
                             (let [fields (field->keyword-fields config)]
                               (vec (concat total-fields fields))))
                           []
                           configs)]
    (reduce (fn [data, key-name]
              (let [value (get doc (keyword key-name))]
                (into data (if (string? value)
                             (vector value)
                             value))))
            '()
            sub-fields)))

(defn- parsed-draft-concept->elastic-doc
  "Generate elastic document for draft records."
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id user-id
                revision-date native-id]} concept
        parsed-concept (if (:deleted concept)
                         (c-parser/parse-concept context concept)
                         parsed-concept)
        ;; No associations in drafts
        concept-type (concepts/concept-id->type (:concept-id concept))
        version (generics/current-generic-version concept-type)
        gen-name (csk/->kebab-case (get-in parsed-concept [:MetadataSpecification :Name] ""))
        gen-ver (get-in parsed-concept [:MetadataSpecification :Version])
        index-data-file (format "schemas/%s/v%s/config.json" (name concept-type) version)
        index-file-raw (slurp (io/resource index-data-file))
        index-data (json/parse-string index-file-raw true)
        ;; No default keyword processing till latter
        common-doc ;; fields common to all generic documents
        {:cmr-version (release-number)
         :concept-id concept-id
         :revision-id revision-id
         :deleted deleted
         :gen-name gen-name
         :gen-name-lowercase (util/safe-lowercase gen-name)
         :gen-version gen-ver
         :generic-type (str gen-name " " gen-ver)
         :provider-id provider-id
         :provider-id-lowercase (util/safe-lowercase provider-id)
         :user-id user-id
         :revision-date revision-date
         :native-id native-id
         :native-id-lowercase (string/lower-case native-id)}
        configs (gen-util/only-elastic-preferences (:Indexes index-data))
        ;; now add the configured indexes
        doc (reduce
             (fn [data, config] (into data (field->index config parsed-concept)))
             common-doc
             configs)]
    (assoc doc :keyword (get-draft-keywords configs doc))))

(doseq [concept-type concepts/get-generic-non-draft-concept-types-array]
  (defmethod esearch/parsed-concept->elastic-doc concept-type
    ;; Public function called by the indexer framework when a document is needed.
    [context concept parsed-concept]
    (parsed-concept->elastic-doc context concept parsed-concept)))

(doseq [concept-type concepts/get-draft-concept-types-array]
  (defmethod esearch/parsed-concept->elastic-doc concept-type
    ;; Public function called by the indexer framework when a document is needed.
    [context concept parsed-concept]
    (parsed-draft-concept->elastic-doc context concept parsed-concept)))

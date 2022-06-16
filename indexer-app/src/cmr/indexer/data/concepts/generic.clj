(ns cmr.indexer.data.concepts.generic
  "Contains functions to parse and convert Generic Documents (that is a document
   complying to a schema supported by the Generic Document system) to and object
   that can be indexed in lucine."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defn- only-elastic-preferences
  "Go through all the index configurations and return only the ones related to 
   generating elastic values. If an index does not specify what type it is for,
   then assume elastic"
  [list-of-indexs]
  (keep #(if (not (nil? %)) %)
        (map
         (fn [x] (when (or (nil? (:Type x)) (= "elastic" (:Type x))) x))
         list-of-indexs)))

(defn jq->list
  "Convert a jq (unix command) style path to a list which can be used by get in
   to drill down into a nested set of maps.
   .Level1.Level2[1].Level3 -> [:Level1 :Level2 1 :Level3]"
  ([jq-path] (jq->list jq-path str))
  ([jq-path namer]
   (into [] (map (fn
                   [value]
                   (if (every? #(Character/isDigit %) value)
                     (Integer/parseInt value)
                     (namer value)))
                 (-> jq-path
                     (string/replace #"^\." "")
                     (string/replace #"\[(\d+)\]" ".$1")
                     (string/split #"\."))))))
  
(defmulti field->index
  "Functions which convert a part of metadata to a name-value which can be added
   to an index document. This is switched by looking for an :Indexer value and
   assuming :default if it is not set"
  (fn [settings _] (or (:Indexer settings) :default)))
  
(defmethod field->index "complex-field"
  [settings data]
  (let [field-list (get settings :Field ".")
        field-data (get-in data (jq->list field-list keyword) {})
        config (get settings :Configuration {})
        sub-fields (get config :sub-fields {})
        layout (get config :format "%s=%s")
        field-name (string/lower-case (:Name settings))
        field-name-lower (str field-name"-lowercase")
        field-value (reduce (fn [data-str, key-name]
                              (str
                               data-str
                               (when-not (empty? data-str) ", ")
                               (format layout key-name (get field-data (keyword key-name)))))
                            ""
                            sub-fields)
        field-value-lower (string/lower-case field-value)]
    {(keyword field-name) field-value
     (keyword field-name-lower) field-value-lower}))

(defmethod field->index :default
  [settings data]
  (let [field-name (string/lower-case (:Name settings))
        field-name-lower (str field-name "-lowercase")
        value (get-in data (jq->list (:Field settings) keyword))
        value-lower (string/lower-case value)]
    {(keyword field-name) value
     (keyword field-name-lower) value-lower}))

(comment
  "Usage of the field-index function:"
  (println (field->index
            {:Name "complex-field"
             :Field ".l1"
             :Indexer "complex-field"
             :Configuration {:sub-fields [:s2] :format "%s == %s"}}
            {:l1 {:s1 "one" :s2 "two"}}))
  (println (field->index {:Field ".l1.l2.l3" :Name "Type"} {:l1 {:l2 {:l3 "value"}}}))
  )

(defn parsed-concept->elastic-doc-without-context
  "Generate an all of the elastic document parts that do not require a context"
  [concept parsed-concept]
  (def my-concept concept) ; remove before flight
  (def my-parsed parsed-concept) ; remove before flight

  (let [{:keys [concept-id revision-id deleted provider-id user-id
                revision-date format-key extra-fields]} concept
        {:keys [tool-name]} extra-fields
        long-name (:LongName parsed-concept) ; should I remove this?
        gen-name (get-in parsed-concept [:MetadataSpecification :Name])
        gen-ver (get-in parsed-concept [:MetadataSpecification :Version])
        index-data-file (format "schemas/%s/v%s/index.json" gen-name gen-ver)
        index-file-raw (slurp (clojure.java.io/resource index-data-file))
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
         :gen-name-lowercase (string/lower-case gen-name)
         :gen-version gen-ver
         :generic-type (str gen-name " " gen-ver)
         :provider-id provider-id
         :provider-id-lowercase (string/lower-case provider-id)
         :keyword keyword-values
         :user-id user-id
         :revision-date revision-date}
        configs (only-elastic-preferences (:Indexes index-data))
        ;; now add the configured indexes
        doc (reduce
             (fn [data, config] (into data (field->index config parsed-concept)))
             common-doc
             configs)]
    (if deleted
      (assoc common-doc :metadata-format (name (mt/format-key format-key))
             :gen-type-lowercase (string/lower-case gen-name)
             :long-name long-name
             :long-name-lowercase (string/lower-case long-name))
      doc)))

(comment
  (parsed-concept->elastic-doc-without-context my-concept my-parsed)
  (println (keyword-util/concept-keys->keyword-text my-parsed [:LongName]))

  )

(defmethod es/parsed-concept->elastic-doc :generic
  [context concept parsed-concept]
  (parsed-concept->elastic-doc-without-context concept parsed-concept))

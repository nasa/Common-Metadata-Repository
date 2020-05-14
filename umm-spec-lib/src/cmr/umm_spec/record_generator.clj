(ns cmr.umm-spec.record-generator
  "Defines functions for generating clojure records that represent the UMM."
  (:require
   [cmr.umm-spec.json-schema :as js]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cmr.umm-spec.models.umm-collection-models]
   [cmr.umm-spec.models.umm-common-models]
   [cmr.umm-spec.models.umm-granule-models]
   [cmr.umm-spec.models.umm-service-models]
   [cmr.umm-spec.models.umm-subscription-models]
   [cmr.umm-spec.models.umm-tool-models]
   [cmr.umm-spec.models.umm-variable-models]))

;; Improvements
;; - generate records with fields in the same order as they are defined in the file.
;; - generate documentation list the type and restrictions

(def schema-name->namespace
  "A map of schema names to the namespace they should be placed in"
  {"umm-cmn-json-schema.json" 'cmr.umm-spec.models.umm-common-models
   "umm-c-json-schema.json" 'cmr.umm-spec.models.umm-collection-models
   "umm-g-json-schema.json" 'cmr.umm-spec.models.umm-granule-models
   "umm-s-json-schema.json" 'cmr.umm-spec.models.umm-service-models
   "umm-sub-json-schema.json" 'cmr.umm-spec.models.umm-subscription-models
   "umm-t-json-schema.json" 'cmr.umm-spec.models.umm-tool-models
   "umm-var-json-schema.json" 'cmr.umm-spec.models.umm-variable-models})

(defn schema-type-constructor
  "Returns the map->RecordName function that can be used to construct a type defined in the JSON
  schema"
  [schema type-name]
  (let [record-ns (schema-name->namespace (:schema-name schema))]
    (-> (str (name record-ns) "/map->" (name type-name))
        symbol
        find-var
        var-get)))

(def ^:private MAX_LINE_SIZE
  "Defines the maximum line size for Clojure files."
  100)

(defn wrap-line
  "Wraps lines so they are at most line-size characters long. Returns a list of lines"
  [line-size text]
  (loop [line nil
         lines []
         [word & words] (str/split text #"\s+")]
    (cond
      ;; No more words means we are finished
      (nil? word)
      (if line
        (conj lines line)
        ;; text was empty
        lines)

      ;; First word of the line
      (nil? line)
      (recur word lines words)

      ;; Word can fit on the current line
      (<= (+ (count line) 1 (count word)) line-size)
      (recur (str line " " word) lines words)

      ;; Word can't fit on the current line
      :else
      (recur word (conj lines line) words))))

(defn- generate-comment
  "Generates a clojure comment string with indentation."
  [indent-size text]
  (let [indent+comment (str (str/join (repeat indent-size " ")) ";; ")
        max-comment-line-size (- MAX_LINE_SIZE (count indent+comment))]
    (str indent+comment
         (str/join (str "\n" indent+comment) (wrap-line max-comment-line-size text)))))

(defn- generate-doc-string
  "Generates a Clojure doc string."
  [text]
  (let [indent "  "
        max-doc-line-size (- MAX_LINE_SIZE (count indent))]
    (str indent "\""
         (->> (str/replace text "\"" "\\\"")
              (wrap-line max-doc-line-size)
              (str/join (str "\n" indent)))
         "\"")))

(def RECORD_COMMENT_INDENTATION
  "The number of spaces that should be indented before a record comment."
  3)

(defn- generate-record-field
  "Generates a single field for a clojure record"
  [{:keys [field-name description]}]
  (let [description-str (when description (generate-comment RECORD_COMMENT_INDENTATION description))
        field-str (str "   " field-name)]
    (if description-str
      (str description-str "\n" field-str)
      field-str)))

(defn- generate-record
  "Generates Clojure record"
  [{:keys [record-name fields description]}]
  (str/join
    "\n"
    (concat
      (when description
        [(generate-comment 0 description)])
      [(str "(defrecord " record-name)
       "  ["]
      [(str/join "\n\n" (map generate-record-field fields))]
      ["  ])"
       (str "(record-pretty-printer/enable-record-pretty-printing " record-name ")")])))

(defn- expand-one-of-any-of-properties
  "Lookup all top level refs from loaded schema, retrieve referenced schema
  and extract properties for oneOf and anyOf and return all properties in a flattened list."
  [loaded-schema type-name]
  (flatten
   (for [type-selector [:oneOf :anyOf]]
     (map :properties
          (js/expand-refs loaded-schema
                          (or (get-in loaded-schema [:definitions type-name type-selector])
                              (get-in loaded-schema [:ref-schemas "umm-cmn-json-schema.json" :definitions type-name type-selector])))))))

(defn- definition->record
  "Converts a JSON Schema definition into a record description if it's appropriate to have a record
  for it. Returns nil otherwise."
  [loaded-schema type-name type-def]
  (when (or (= "object" (:type type-def)) (:oneOf type-def) (:anyOf type-def))
    (let [merged-properties (apply merge
                                   (:properties type-def)
                                   (expand-one-of-any-of-properties loaded-schema type-name))]
      {:record-name (name type-name)
       :description (:description type-def)
       :fields (for [[property-name prop-def] merged-properties]
                 {:field-name (name property-name)
                  :description (:description prop-def)})})))

(defn- generate-clojure-records
  "Generates a string containing clojure record definitions from the given schema."
  [schema loaded-schema]
  (let [definitions (:definitions schema)
        definitions (if (:title schema)
                      ;; The schema itself can define a top level object
                      (cons [(keyword (:title schema)) (dissoc schema :definitions)]
                            definitions)
                      definitions)
        records-strings (->> definitions
                             (map #(apply definition->record loaded-schema %))
                             (remove nil?)
                             (map generate-record))]
    (str/join "\n\n" records-strings)))

(defn- generate-ns-declaration
  "Generates a namespace declaration for a namespace file containing UMM records."
  [{:keys [the-ns description]}]
  (format "(ns %s\n %s\n (:require [cmr.common.dev.record-pretty-printer :as record-pretty-printer]))"
          (name the-ns)
          (generate-doc-string description)))

(defn- generated-file-warning
  "A comment placed at the top of a generated file to warn it was generated."
  [source-resource]
  (let [file-name (last (str/split (str source-resource) #"/"))]
    (format ";; WARNING: This file was generated from %s. Do not manually modify."
            file-name)))

(defn generate-clojure-records-file
  "Generates a file containing clojure records for the types defined in the UMM JSON schema."
  [{:keys [the-ns schema-resource loaded-schema] :as ns-def}]
  (let [schema (js/load-json-resource schema-resource)
        file-name (str "src/"
                       (-> the-ns
                           name
                           (str/replace "." "/")
                           (str/replace "-" "_"))
                       ".clj")
        file-contents (str (generated-file-warning schema-resource)
                           "\n"
                           (generate-ns-declaration ns-def)
                           "\n\n"
                           (generate-clojure-records schema loaded-schema))]
    (.. (io/file file-name) getParentFile mkdirs)
    (spit file-name file-contents)))

(defn generate-umm-records
  "Generates all the UMM records"
  []
  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-common-models
    :description "Defines UMM Common clojure records."
    :schema-resource js/umm-cmn-schema-file
    :loaded-schema (get-in (js/concept-schema :collection) [:ref-schemas "umm-cmn-json-schema.json"])})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-collection-models
    :description "Defines UMM-C clojure records."
    :schema-resource (js/concept-schema-resource :collection)
    :loaded-schema (js/concept-schema :collection)})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-granule-models
    :description "Defines UMM-G clojure records."
    :schema-resource (js/concept-schema-resource :granule)
    :loaded-schema (js/concept-schema :granule)})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-service-models
    :description "Defines UMM-S clojure records."
    :schema-resource (js/concept-schema-resource :service)
    :loaded-schema (js/concept-schema :service)})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-subscription-models
    :description "Defines UMM-Sub clojure records."
    :schema-resource (js/concept-schema-resource :subscription)
    :loaded-schema (js/concept-schema :subscription)})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-tool-models
    :description "Defines UMM-T clojure records."
    :schema-resource (js/concept-schema-resource :tool)
    :loaded-schema (js/concept-schema :tool)})

  (generate-clojure-records-file
   {:the-ns 'cmr.umm-spec.models.umm-variable-models
    :description "Defines UMM-Var clojure records."
    :schema-resource (js/concept-schema-resource :variable)
    :loaded-schema (js/concept-schema :variable)}))

(comment
  (generate-umm-records)
  (generated-file-warning js/umm-c-schema-file))

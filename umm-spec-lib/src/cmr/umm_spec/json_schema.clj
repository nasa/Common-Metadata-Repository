(ns cmr.umm-spec.json-schema
  "This contains code for loading UMM JSON schemas."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.common.log :as log]
            [cheshire.core :as json]
            [cheshire.factory :as factory]
            [cmr.common.date-time-parser :as dtp]
            [cmr.common.validations.json-schema :as js-validations]
            [cmr.common.util :as util]
            [cmr.umm-spec.versioning :as ver]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defined schema files
(def concept-schema-name
  "A map of concept types to schema names."
  {:collection "umm-c-json-schema.json"
   :service    "umm-s-json-schema.json"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Code for loading schema files.

(defn load-json-resource
  "Loads a json resource from the resource url. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))

(defn reference-processor-selector
  "Used to determine how to process a type definition when resolving references."
  [type-def]
  (or (:type type-def)
      (cond
        (:$ref type-def) :$ref
        ;; This is needed for a nested properties def in an allOf
        (:properties type-def) "object"

        ;; This will trigger an error
        :else
        (throw (Exception. (str "Unable to resolve ref on " (pr-str type-def)))))))

(defmulti ^:private resolve-ref
  "Recursively resolves references to other types within the JSON schema type definition. When a
  reference is found it is replaced with a map containing the type name referenced and the name of
  the other schema that was referenced."
  (fn [schema-name type-def]
    (reference-processor-selector type-def)))

(defn- resolve-ref-deflist
  "Resolves all the references in the sub type definitions of a map of type names and type
  definitions."
  [schema-name definition-map]
  (into {} (for [[n type-def] definition-map]
             [n (resolve-ref schema-name type-def)])))

(defn- resolve-one-of
  "Resolves oneOf definitions in object type."
  [schema-name object]
  (if (:oneOf object)
      (update-in object [:oneOf]
                 (fn [one-ofs]
                   (map (fn [o]
                          (if (:properties o)
                            (update-in o [:properties] (partial resolve-ref-deflist schema-name))
                            o))
                        one-ofs)))
      object))

(defmethod resolve-ref :$ref
  [schema-name type-def]
  (let [[ref-schema-name _ type-name] (str/split (:$ref type-def) #"/")]
    (assoc type-def :$ref (if (= ref-schema-name "#")
                            {:schema-name schema-name
                             :type-name (keyword type-name)}
                            {:schema-name (str/replace ref-schema-name "#" "")
                             :type-name (keyword type-name)}))))

(defmethod resolve-ref "object"
  [schema-name type-def]
  (let [updated (if (:properties type-def)
                  (update-in type-def [:properties] (partial resolve-ref-deflist schema-name))
                  type-def)]
    (->> updated
         (resolve-one-of schema-name)
         ;; other resolvers can go here, e.g. for allOf
         )))

(defmethod resolve-ref "array"
  [schema-name type-def]
  (update-in type-def [:items] (partial resolve-ref schema-name)))

;; No resolution
(doseq [t ["string" "integer" "number" "boolean" :empty-map]]
  (defmethod resolve-ref t [_ type-def] type-def))

(defmulti ^:private referenced-schema-names
  "Returns a list of the referenced schema names from a loaded schema"
  #'reference-processor-selector)

(defmethod referenced-schema-names :default
  [_]
  nil)

(defmethod referenced-schema-names :$ref
  [type-def]
  (when-let [schema-name (get-in type-def [:$ref :schema-name])]
    [schema-name]))

(defmethod referenced-schema-names "object"
  [type-def]
  (concat
    (mapcat referenced-schema-names (vals (:properties type-def)))
    (mapcat referenced-schema-names (mapcat (comp vals :properties) (:oneOf type-def)))))

(defmethod referenced-schema-names "array"
  [type-def]
  (referenced-schema-names (:items type-def)))

(defn umm-schema-resource
  "Returns a resource URL for the given schema name and optional UMM version. If umm-version is
   not specified, the current version is returned."
  ([schema-name]
   (umm-schema-resource ver/current-version schema-name))
  ([umm-version schema-name]
   (io/resource (str "json-schemas/umm/v" umm-version "/" schema-name))))

(defn concept-schema-resource
  "Returns a resource URL for the specified UMM concept type keyword."
  ([concept-type]
   (concept-schema-resource ver/current-version concept-type))
  ([umm-version concept-type]
   (umm-schema-resource umm-version (concept-schema-name concept-type))))

(defn- load-schema
  "Loads a JSON schema into a Clojure structure. Returns a map with the following keys

  * :definitions - A map of types that were defined in this schema.
  * :schema-name - the name of the schema that was loaded.
  * :root - A keyword for the name of root type defined in this schema. This will be a key defined
  in the definitions map. May be nil if no root type is defined.
  * :ref-schemas - A map of schema name to schemas that are referenced within this schema."
  [umm-version schema-name]
  (let [parsed (load-json-resource (umm-schema-resource umm-version schema-name))
        definitions (resolve-ref-deflist schema-name (get parsed :definitions))
        root-def (when (:title parsed)
                   (resolve-ref schema-name (dissoc parsed :definitions :$schema :title)))
        definitions (if root-def
                      (assoc definitions (keyword (:title parsed)) root-def)
                      definitions)
        referenced-schemas (into #{} (concat (mapcat referenced-schema-names (vals definitions))
                                             (when root-def
                                               (referenced-schema-names root-def))))
        ;; Remove this schema
        referenced-schemas (disj referenced-schemas schema-name)]
    {:definitions definitions
     :schema-name schema-name
     :root (keyword (:title parsed))
     :ref-schemas (into {} (for [ref-schema-name referenced-schemas]
                             [ref-schema-name (load-schema umm-version ref-schema-name)]))}))

(defn lookup-ref
  "Looks up a ref in a loaded schema. Returns the schema containing the referenced type and the ref.
  The ref may refer to this schema or an external schema referenced by this schema."
  [schema the-ref]
  (let [{:keys [schema-name type-name]} (:$ref the-ref)
        result (if (= schema-name (:schema-name schema))
                 ;; Refers to this schema
                 [schema (get-in schema [:definitions type-name])]
                 ;; Refers to a referenced schema
                 (lookup-ref (get-in schema [:ref-schemas schema-name]) the-ref))]
    (or result
        (throw (Exception. (str "Unable to load ref " (pr-str the-ref)))))))

(defn concept-schema*
  ([concept-type]
    ;; Default to the current UMM version.
    (concept-schema* ver/current-version concept-type))
  ([umm-version concept-type]
    (load-schema umm-version (concept-schema-name concept-type))))

;; Define a memoized version of concept-schema to cache loaded JSON schemas.
(def concept-schema
  "Returns a Clojure map describing the JSON schema for the given UMM concept type."
  (memoize concept-schema*))

(defn- concept-schema-java*
  [umm-version concept-type]
  (let [schema-url (umm-schema-resource umm-version (concept-schema-name concept-type))]
    (js-validations/parse-json-schema-from-uri schema-url)))

(def concept-schema-java (memoize concept-schema-java*))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation

(defn validate-umm-json
  "Validates the UMM JSON and returns a list of errors if invalid."
  ([json-str concept-type]
   (validate-umm-json json-str concept-type ver/current-version))
  ([json-str concept-type umm-version]
   (let [schema-name (concept-schema-name concept-type)
         schema-url (umm-schema-resource umm-version schema-name)]
     (if schema-url
       (let [java-schema-obj (js-validations/parse-json-schema-from-uri schema-url)]
         (js-validations/validate-json java-schema-obj json-str))
       [(str "Unknown UMM JSON schema version: " (pr-str umm-version))]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Loaded schemas

(def umm-c-schema (concept-schema :collection))

(def umm-s-schema (concept-schema :service))

(def umm-cmn-schema-file
  "The schema required to parse umm-common"
  (umm-schema-resource ver/current-version "umm-cmn-json-schema.json"))

(defn root-def
  "Returns the root type definition of the given schema."
  [schema]
  (get-in schema [:definitions (:root schema)]))

(def schema-ns-map
  "A map of schema names to the namespace they should be placed in"
  {"umm-cmn-json-schema.json" 'cmr.umm-spec.models.common
   "umm-c-json-schema.json" 'cmr.umm-spec.models.collection
   "umm-s-json-schema.json" 'cmr.umm-spec.models.service})

(defn- record-ctor
  "Returns the map->RecordName function that can be used to construct a type defined in the JSON
  schema"
  [schema type-name]
  (let [record-ns (schema-ns-map (:schema-name schema))]
    (-> (str (name record-ns) "/map->" (name type-name))
        symbol
        find-var
        var-get)))

(defn- resolve-$refs
  "Recursively resolves $refs, as some are multi-level $refs to other types."
  [[schema definition :as pair]]
  (if (:$ref definition)
    (recur (apply lookup-ref pair))
    pair))

(defn- parse-error-msg
  [type-def val]
  (str "Could not parse "
       (or (:format type-def)
           (:type type-def))
       " value: "
       val))

(defn coerce
  "Returns x coerced according to a JSON schema type type definition."
  ([schema x]
   (coerce schema (root-def schema) x))
  ([schema type-definition x]
   (let [type-name (or (-> type-definition :$ref :type-name)
                       (:root schema))
         [schema type-definition] (resolve-$refs [schema type-definition])]
     (case (:type type-definition)

       "string"  (case (:format type-definition)
                   "date-time" (if (instance? org.joda.time.DateTime x)
                                 x
                                 (dtp/parse-datetime x))
                   ;; else...
                   (str x))

       "number"  (if (number? x)
                   x
                   (Double. x))

       "integer" (if (integer? x)
                   x
                   (Long. x))

       "boolean" (if (string? x)
                   (= "true" x)
                   (boolean x))

       ;; The most important job of this function:
       "object"  (let [ctor (record-ctor schema type-name)
                       ;; Reduce an empty map with each pair of (non-nil) key/vals in x, trying to
                       ;; parse each value or else add error messages in the map under :_errors.
                       m (reduce
                          (fn [m [k v]]
                            (let [prop-type-definition (get-in type-definition [:properties k])
                                  item-type-def (:items prop-type-definition)]
                              (if (= "array" (:type prop-type-definition))
                                (let [results (for [x v
                                                    ;; skip nil inputs
                                                    :when (some? x)]
                                                (try
                                                  (let [coerced (coerce schema item-type-def x)]
                                                    (when (some? coerced)
                                                      {:value coerced}))
                                                  (catch Exception e
                                                    (let [msg (parse-error-msg item-type-def x)]
                                                      {:error msg}))))
                                      results (remove nil? results)]
                                  (cond-> m
                                    (seq results) (assoc k (mapv :value results))
                                    (some :error results) (assoc-in [:_errors k] (mapv :error results))))
                                ;; non-array types
                                (try
                                  (let [parsed (coerce schema prop-type-definition v)]
                                    (if (some? parsed)
                                      (assoc m k parsed)
                                      m))
                                  (catch Exception e
                                    (let [msg (parse-error-msg prop-type-definition v)]
                                      (assoc-in m [:_errors k] msg)))))))
                          nil
                          (filter val x))]
                   ;; Return nil instead of empty maps/records here.
                   (when m
                     (ctor m)))

       ;; Otherwise, return the value itself:
       x))))

(defn parse-umm-c
  [x]
  (coerce umm-c-schema x))

(comment
  (coerce umm-c-schema
          {:EntryTitle "This is a test"
           :TemporalExtents [{:EndsAtPresentFlag "true"
                              :SingleDateTimes ["2000-01-01T00:00:00.000Z"]}]
           :Distributions [{:Fees "123.4"
                            :Sizes [{:Size "123" :Unit "MB"}]}]})
  )

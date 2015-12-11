(ns cmr.umm-spec.json-schema
  "This contains code for loading UMM JSON schemas."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.common.validations.json-schema :as js-validations]
            [cmr.umm-spec.util :as spec-util]
            [cmr.common.util :as cmn-util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defined schema files
(def concept-type->schema-file
  "Maps a concept type to the schema required to parse it."
  {:collection (io/resource "json-schemas/umm-c-json-schema.json")
   :service (io/resource "json-schemas/umm-s-json-schema.json")})

(def umm-cmn-schema-file
  "The schema required to parse umm-common"
  (io/resource "json-schemas/umm-cmn-json-schema.json"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation

(def concept-type->schemas
  "Maps a concept type to the parsed & validated schema file."
  (cmn-util/map-values #(js-validations/parse-json-schema-from-uri (str %))
                   concept-type->schema-file))

(defn validate-umm-json
  "Validates the UMM JSON and returns a list of errors if invalid."
  ([json-str concept-type]
    (js-validations/validate-json (get concept-type->schemas concept-type) json-str)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Code for loading schema files.

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
    (if (:oneOf updated)
      (update-in updated [:oneOf]
                 (fn [one-ofs]
                   (map (fn [o]
                          (if (:properties o)
                            (update-in o [:properties] (partial resolve-ref-deflist schema-name))
                            o))
                        one-ofs)))
      updated)))

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

(defn- load-schema
  "Loads a JSON schema into a Clojure structure. Returns a map with the following keys

  * :definitions - A map of types that were defined in this schema.
  * :schema-name - the name of the schema that was loaded.
  * :root - A keyword for the name of root type defined in this schema. This will be a key defined
  in the definitions map. May be nil if no root type is defined.
  * :ref-schemas - A map of schema name to schemas that are referenced within this schema."
  [schema-name]
  (let [parsed (spec-util/load-json-resource (io/resource (str "json-schemas/" schema-name)))
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
                             [ref-schema-name (load-schema ref-schema-name)]))}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Loaded schemas

(def umm-c-schema (load-schema "umm-c-json-schema.json"))
(def umm-s-schema (load-schema "umm-s-json-schema.json"))
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

(defn coerce
  "Returns x coerced according to a JSON schema type type definition. With no other parameters, the
  schema and type defaults to the umm-c-schema and the root UMM-C type."
  ([x]
   (coerce umm-c-schema x))
  ([schema x]
   (coerce schema (root-def schema) [] x))
  ([schema definition key-path x]
   (let [type-name (or (-> definition :$ref :type-name)
                       (:root schema))
         [schema definition] (resolve-$refs [schema definition])]
     (condp = (:type definition)

       "string"  (condp = (:format definition)
                   "date-time"
                   (if (instance? org.joda.time.DateTime x)
                     x
                     (try (spec-util/parse-datetime x)
                       (catch Exception e
                         (throw (IllegalArgumentException.
                                  (format "Failed to parse date-time [%s] at key-path [%s]"
                                          x (pr-str (vec key-path)))
                                  e)))))
                   (str x))

       "number"  (cond (number? x) x
                       (string? x) (when-not (str/blank? x)
                                     (Double. x))
                       :else (throw (Exception. (str "Unexpected type for number: " (pr-str x)))))

       "integer" (cond (integer? x) x
                       (string? x) (when-not (str/blank? x)
                                     (Long. x))
                       :else (throw (Exception. (str "Unexpected type for integer: " (pr-str x)))))

       "boolean" (if (string? x)
                   (= "true" x)
                   (boolean x))

       ;; Return nil instead of empty vectors.
       "array"   (when-let [coerced (seq (keep #(coerce schema (:items definition) key-path %) x))]
                   (vec coerced))

       "object"  (let [ctor (record-ctor schema type-name)
                       kvs (for [[k v] (filter val x)]
                             (let [prop-definition (get-in definition [:properties k])
                                   v (coerce schema prop-definition (conj key-path k) v)]
                               (when (some? v)
                                 [k v])))
                       m (into {} kvs)]
                   ;; Return nil instead of empty maps/records here.
                   (when (seq m)
                     (ctor m)))

       ;; Otherwise...
       (throw (IllegalArgumentException. (str "Don't know how to coerce value "
                                              (pr-str x)
                                              " at key path "
                                              (pr-str (vec key-path))
                                              " using JSON schema type ["
                                              (pr-str definition) "]")))))))

(comment
  (coerce {:EntryTitle "This is a test"
           :TemporalExtents (list
                              {:EndsAtPresentFlag "true"
                               :SingleDateTimes ["2000-01-01T00:00:00.000Z"]})
           :Distributions [{:Fees "123.4"}]})
  )

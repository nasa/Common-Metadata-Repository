(ns cmr.umm-spec.json-schema
  "This contains code for loading UMM JSON schemas."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cmr.umm-spec.util :as spec-util]
            [cmr.common.validations.json-schema :as js-validations]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defined schema files

(def umm-cmn-schema-file (io/resource "json-schemas/umm-cmn-json-schema.json"))

(def umm-c-schema-file (io/resource "json-schemas/umm-c-json-schema.json"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validation

(def ^:private json-schema-for-validation
  (js-validations/parse-json-schema-from-uri (str umm-c-schema-file)))

(defn validate-umm-json
  "Validates the UMM JSON and returns a list of errors if invalid."
  [json-str]
  (js-validations/validate-json json-schema-for-validation json-str))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Code for loading schema files.

(defn reference-processor-selector
  "Used to determine how to process a type definition when resolving references."
  [type-def]
  (or (:type type-def)
      (cond
        (:$ref type-def) :$ref
        (:allOf type-def) :allOf
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

(defmethod resolve-ref :allOf
  [schema-name type-def]
  (update-in type-def [:allOf] #(map (partial resolve-ref schema-name) %)))

(defmethod resolve-ref "object"
  [schema-name type-def]
  (update-in type-def [:properties] (partial resolve-ref-deflist schema-name)))

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

(defmethod referenced-schema-names :allOf
  [type-def]
  (mapcat referenced-schema-names (:allOf type-def)))

(defmethod referenced-schema-names "object"
  [type-def]
  (mapcat referenced-schema-names (vals (:properties type-def))))

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

(def umm-c-schema-small (load-schema "umm-c-json-schema-small.json"))



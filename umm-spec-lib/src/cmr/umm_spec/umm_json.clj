(ns cmr.umm-spec.umm-json
  "Contains functions for converting a UMM into JSON and back out of JSON."
  (:require
   [cheshire.core :as json]
   [clojure.set :as set]
   [cmr.common.date-time-parser :as dtp]
   ;; To get ability to convert joda time to json
   [cmr.common.joda-time]
   [cmr.umm-spec.json-schema :as js]
   [cmr.umm-spec.migration.version.core :as vm]
   [cmr.umm-spec.record-generator :as record-gen]
   [cmr.umm-spec.versioning :as ver]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UMM To JSON

(defn to-jsonable
  [x]
  (cond
    (map? x) (into {}
                   (for [[k v] x
                         ;; Skip over namespaced keywords. We add extra metadata into a collection
                         ;; for things like humanizers and parsed additional attribute values.
                         :when (not (namespace k))
                         :when (some? v)]
                     [(to-jsonable k) (to-jsonable v)]))
    (sequential? x) (mapv to-jsonable x)
    :else x))

(defn umm->json
  "Returns a JSON string from the given Clojure record."
  [umm-record]
  (json/generate-string (to-jsonable umm-record)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; JSON to UMM

(defmulti ^:private parse-json
  "Takes a Clojure value converted directly from JSON data and maps it into the correct UMM types
  using the given JSON schema, type name, and schema. Clojure has ways to return data directly from
  JSON, however the types will not be correct in some cases. We prefer to have the Clojure records
  instead of maps and real date times instead of strings.

  Argument descriptions
  * schema - This is a JSON schema instance as defined in cmr.umm-spec.json-schema. It's necessary to
  pass the whole schema in so that references can be looked up.
  * type-name-path - This contains the path of type names up to the current type being parsed. Used
  for debugging problems in parsing.
  * type-name - Where applicable this is the name of the object type being parsed. This is used to
  locate the correct UMM Clojure Record.
  * schema-type - the is the definition of the type being parsed from the schema.
  * js-data - This is Clojure data that was parsed initially from a JSON string."
  (fn [schema type-name-path type-name schema-type js-data]
    (cond
      (:type schema-type) (:type schema-type)
      (:oneOf schema-type) "oneOf"
      (:anyOf schema-type) "anyOf"
      (:$ref schema-type) :$ref)))

(defmethod parse-json :default
  [_ type-name-path _ schema-type _]
  (throw (Exception. (str "Unable to parse json for " (pr-str schema-type)
                          " at path " (pr-str type-name-path)))))

;; An object is parsed by finding the equivalent clojure record and it's map->record-name constructor
;; function.
(defmethod parse-json "object"
  [schema type-name-path type-name schema-type js-data]
  (let [constructor-fn (record-gen/schema-type-constructor schema type-name)
        merged-prop-types (apply merge (:properties schema-type)
                                 (concat (map :properties (:oneOf schema-type))
                                         (map :properties (:anyOf schema-type))))
        properties (into {}
                         (for [[k v] js-data
                               :let [sub-type-def (get merged-prop-types k)]]
                           [k (parse-json schema (conj type-name-path k) k sub-type-def v)]))]
    (constructor-fn properties)))

(defmethod parse-json "oneOf"
  [schema type-name-path type-name schema-type js-data]
  (let [constructor-fn (record-gen/schema-type-constructor schema type-name)
        merged-prop-types (apply merge
                                 (:properties schema-type)
                                 (map :properties (js/expand-refs schema (:oneOf schema-type))))
        properties (into {}
                         (for [[k v] js-data
                               :let [sub-type-def (get merged-prop-types k)]]
                           [k (parse-json schema (conj type-name-path k) k sub-type-def v)]))]
    (constructor-fn properties)))

(defmethod parse-json "anyOf"
  [schema type-name-path type-name schema-type js-data]
  (let [constructor-fn (record-gen/schema-type-constructor schema type-name)
        merged-prop-types (apply merge
                                 (:properties schema-type)
                                 (map :properties (js/expand-refs schema (:anyOf schema-type))))
        properties (into {}
                         (for [[k v] js-data
                               :let [sub-type-def (get merged-prop-types k)]]
                           [k (parse-json schema (conj type-name-path k) k sub-type-def v)]))]
    (constructor-fn properties)))

;; A ref refers to another type. We lookup that type and then parse the JSON data using that type.
(defmethod parse-json :$ref
  [schema type-name-path type-name schema-type js-data]
  (let [[ref-schema ref-schema-type] (js/lookup-ref schema schema-type)
        type-name (get-in schema-type [:$ref :type-name])]
    (parse-json ref-schema
                (conj type-name-path type-name)
                type-name
                ref-schema-type
                js-data)))

;; A string has additional information that might change its type in Clojure.
(defmethod parse-json "string"
  [_ _ _ schema-type js-data]
  (if (= (:format schema-type) "date-time")
    (dtp/parse-datetime js-data)
    js-data))

;; These types are parsed correctly from JSON.
(doseq [simple-type ["number" "integer" "boolean"]]
  (defmethod parse-json simple-type
    [_ _ _ _ js-data]
    js-data))

(defmethod parse-json "array"
  [schema type-name-path type-name schema-type js-data]
  (when (seq js-data)
    (mapv
     #(parse-json schema type-name-path type-name (:items schema-type) %)
     js-data)))

(defn json->umm
  "Parses the JSON string and returns Clojure UMM record in the current UMM version."
  ([context concept-type json-str]
   ;; default to trying to parse json string according to the current UMM version
   (json->umm context concept-type json-str (ver/current-version concept-type)))
  ([context concept-type json-str original-umm-version]
   (let [schema (js/concept-schema concept-type)
         root-type-def (get-in schema [:definitions (:root schema)])
         json-obj (json/decode json-str true)
         migrated (vm/migrate-umm
                   context concept-type original-umm-version
                   (ver/current-version concept-type) json-obj)]
     (parse-json schema [(:root schema)] (:root schema) root-type-def migrated))))

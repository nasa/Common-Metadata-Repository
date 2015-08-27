(ns cmr.umm-spec.umm-json
  "Contains functions for converting a UMM into JSON and back out of JSON."
  (:require [cheshire.core :as json]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.record-generator :as record-gen]
            [cmr.common.date-time-parser :as dtp]

            ;; To get ability to convert joda time to json
            [cmr.common.joda-time]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UMM To JSON

(defprotocol ToJsonAble
  "A function for converting data into data that can easily be converted to JSON"
  (to-jsonable
    [o]
    "Takes in object and returns a new object that can be converted to JSON"))

(extend-protocol ToJsonAble

  ;; Records contain every key with a value of nil. We don't want the JSON to contain that.
  ;; This converts them into a standard map without the nil keys.
  clojure.lang.IRecord
  (to-jsonable
    [r]
    (into {}
          (for [[k v] r
                :when (some? v)]
            [(to-jsonable k) (to-jsonable v)])))

  clojure.lang.Sequential
  (to-jsonable
    [values]
    (mapv to-jsonable values))

  ;; Default implementations
  Object
  (to-jsonable [o] o)

  nil
  (to-jsonable [o] o))

(defn umm->json
  "Converts the UMM record to JSON."
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
        properties (into {}
                         (for [[k v] js-data
                               :let [sub-type-def (get-in schema-type [:properties k])]]
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
  (mapv #(parse-json schema type-name-path type-name (:items schema-type) %) js-data))

(defn json->umm
  "Parses the JSON string and returns Clojure UMM records."
  [schema json-str]
  (let [root-type-def (get-in schema [:definitions (:root schema)])]
    (parse-json schema [(:root schema)] (:root schema) root-type-def (json/decode json-str true))))




(ns cmr.schema-validation.json-schema
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string])
  (:import
   (org.everit.json.schema Schema ValidationException)
   (org.everit.json.schema.loader SchemaLoader SchemaClient)
   (org.json JSONException JSONTokener))
  (:gen-class
   :name cmr.validation.jsonSchema
   :methods [^:static [parseSchemaFromPath [String] org.everit.json.schema.Schema]
             ^:static [parseSchemaFromString [String] org.everit.json.schema.Schema]
             ^:static [parseSchemaFromURI [String] org.everit.json.schema.Schema]
             ^:static [parseSchemaFromURI [java.net.URI] org.everit.json.schema.Schema]
             ^:static [validateJson [org.everit.json.schema.Schema String Boolean] java.util.List]
             ^:static [validateJson [org.everit.json.schema.Schema String] java.util.List]]))

(defn- json-string->JSONType
  "Takes JSON as a string and returns a org.json.JSONObject. or org.json.JSONArray
  Throws a JSONException if the provided JSON is not valid JSON."
  [^String json-string]
  (let [tokener (->> json-string
                     string/trim
                     (JSONTokener.))
        json-type (.nextValue tokener)]
    (if (.more tokener)
      (throw (.syntaxError tokener "Trailing characters are not permitted."))
      json-type)))

(defn json-string->json-schema
  "Convert a string to org.everit.json.schema.Schema object.
  Throws a JSONException if the JSON is not valid.
  Throws SchemaException on un-parseable schema definition."
  [schema-string]
  (SchemaLoader/load (json-string->JSONType schema-string)))

(defn parse-json-schema
  "Convert a Clojure object to a JSON string then parses into a
  org.everit.json.schema.Schema object."
  [schema-def]
  (->> (assoc schema-def :$schema "http://json-schema.org/draft-04/schema#")
       json/generate-string
       json-string->json-schema))

(defn parse-json-schema-from-path
  "Loads a JSON schema from a resource on the classpath
  into a org.everit.json.schema.Schema object. It's necessary
  to use this one if loading a JSON schema from the
  classpath that references other JSON schemas on the classpath.
  Returns nil if the resource is not found.
  Throws JSONException if the JSON is invalid.
  Throws SchemaException on un-parseable schema definition."
  ([path]
   (parse-json-schema-from-path path (io/resource path)))
  ([path uri]
   (when uri
     (-> (SchemaLoader/builder)
         (.schemaClient (SchemaClient/classPathAwareClient))
         (.schemaJson (json-string->JSONType (slurp (str uri))))
         (.resolutionScope (str "classpath://" path))
         .build
         .load
         .build))))

(defn parse-json-schema-from-uri
  "Loads a JSON schema from a uri into a org.everit.json.schema.Schema
  object. It's necessary to use this one if loading a JSON schema
  that references other JSON schemas in the same directory.
  Throws JSONException if the JSON is invalid.
  Throws SchemaException on un-parseable schema definition."
  [uri]
  (let [str-uri (str uri)]
    (-> (SchemaLoader/builder)
        (.schemaJson (json-string->JSONType (slurp str-uri)))
        (.resolutionScope str-uri)
        .build
        .load
        .build)))

(defn- validate-json-with-throw
  "Validate json and throw exceptions."
  [^Schema json-schema json-to-validate]
  (let [raw-json (json-string->JSONType json-to-validate)]
    (.validate json-schema raw-json)))

(defn- validate-json-without-throw
  "Validate json and return error messages."
  [^Schema json-schema json-to-validate]
  (try
    (validate-json-with-throw json-schema json-to-validate)
    (catch JSONException e
      [(.getMessage e)])
    (catch ValidationException e
      (.getAllMessages e))))

(defn validate-json
  "Performs schema validation using the provided JSON schema and the given
  json string to validate. Uses org.everit.json.schema to perform the
  validation. The JSON schema must be provided as a
  org.everit.json.schema.Schema object and the json-to-validate must
  be a string.

  When throw? is set to true,
  Throws:
   JSONException on malformed JSON.
   ValidationException on validation failures.
  throw? is set to false:
    Return list of validation errors.
    nil if no errors.

  For details, see:
  * [Schema](http://erosb.github.io/everit-json-schema/javadoc/1.11.0/)"
  ([^Schema json-schema json-to-validate]
   (validate-json json-schema json-to-validate false))
  ([^Schema json-schema json-to-validate throw?]
   (if throw?
     (validate-json-with-throw json-schema json-to-validate)
     (validate-json-without-throw json-schema json-to-validate))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                            Java Wrappers                                   ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -parseSchemaFromString
  [^String string]
  (json-string->json-schema string))

(defn -parseSchemaFromPath
  [^String string]
  (parse-json-schema-from-path string))

(defn -parseSchemaFromURI
  [^String uri]
  (parse-json-schema-from-uri uri))

(defn -parseSchemaFromURI
  [^java.net.URI uri]
  (parse-json-schema-from-uri uri))

(defn -validateJson
  ([^Schema schema ^String string]
   (validate-json schema string))
  ([^Schema schema ^String string ^Boolean throwFlag]
   (validate-json schema string throwFlag)))

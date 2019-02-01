(ns cmr.common.validations.json-schema
  "Functions used to perform JSON schema validation. See http://json-schema.org/
  for more details."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.log :as log :refer (warn info)]
   [cmr.common.services.errors :as errors])
  (:import
   (org.everit.json.schema Schema ValidationException)
   (org.everit.json.schema.loader SchemaLoader SchemaClient)
   (org.json JSONArray JSONException JSONObject JSONTokener)))

(defn- json-string->JSONType
  "Takes JSON as a string and returns a org.json.JSONObject. or org.json.JSONArray
   Throws an exception if the provided JSON is not valid JSON."
  [^String json-string]
  (try
    (.nextValue (JSONTokener. json-string))
    (catch JSONException e
      (errors/throw-service-error :bad-request (str "Invalid JSON: " (.getMessage e))))))

(defn json-string->json-schema
  "Convert a string to org.everit.json.schema.Schema object."
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
  "Loads a JSON schema from a resource into a org.everit.json.schema.Schema
  object. It's necessary to use this one if loading a JSON schema from the
  classpath that references other JSON schemas on the classpath."
  ([path]
   (when-let [uri (io/resource path)]
     (parse-json-schema-from-path path uri)))
  ([path uri]
   (-> (SchemaLoader/builder)
       (.schemaClient (SchemaClient/classPathAwareClient))
       (.schemaJson (json-string->JSONType (slurp (str uri))))
       (.resolutionScope (str "classpath://" path))
       .build
       .load
       .build)))

(defn validate-json
  "Performs schema validation using the provided JSON schema and the given
  json string to validate. Uses org.everit.json.schema to perform the
  validation. The JSON schema must be provided as a
  org.everit.json.schema.Schema object and the json-to-validate must
  be a string. Returns a list of the errors found.

  For details, see:
  * [Schema](http://erosb.github.io/everit-json-schema/javadoc/1.11.0/)"
  [^Schema json-schema json-to-validate]
  (try
    (.validate json-schema (json-string->JSONType json-to-validate))
    (catch ValidationException e
      (let [message (seq (.getAllMessages e))]
        (info (str "UMM Validation error. Full message: " (pr-str message)))
        message))))

(defn validate-json!
  "Validates the JSON string against the given schema. Throws a service error
  if it is invalid."
  [schema json-str]
  (when-let [errors (seq (validate-json schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

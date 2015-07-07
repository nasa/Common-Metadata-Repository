(ns cmr.common.validations.json-schema
  "Functions used to perform JSON schema validation. See http://json-schema.org/ for more details."
  (:require [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :as log :refer (warn)]
            [clojure.string :as str])
  (:import com.github.fge.jsonschema.main.JsonSchemaFactory
           com.github.fge.jackson.JsonLoader
           com.fasterxml.jackson.core.JsonParseException))

(defn- parse-error-report
  "Parses the error-report to return a human friendly error message.

  Example:
  {:instance {:pointer \"/provider\"}
   :message \"object instance has properties which are not allowed by the schema: [\"123\"]\"
  ... other keys ignored}"
  [error-report]
  (let [pointer (get-in error-report [:instance :pointer])]
    (str (when-not (str/blank? pointer)
           (str pointer " "))
         (:message error-report))))

(defn- parse-nested-error-report
  "Parses nested error messages from within an error report. See comment block at bottom of file
  for an example nested error report."
  [nested-error-report]
  (for [map-key (keys nested-error-report)
        :when (re-matches #".*oneOf.*" (name map-key))
        sub-error-report (map-key nested-error-report)]
    (parse-error-report sub-error-report)))

(defn- parse-validation-report
  "Takes a validation report and returns a sequence of any errors contained in the report. Returns
  nil if there are no errors. Takes a com.github.fge.jsonschema.core.report.ListProcessingReport.
  See http://fge.github.io/json-schema-validator/2.2.x/index.html for details."
  [report]
  (flatten
    (for [error-report (seq (.asJson report))
          :let [json-error-report (json/decode (.toString error-report) true)]]
      (conj (parse-nested-error-report (:reports json-error-report))
            (parse-error-report json-error-report)))))

(defn- json-string->JsonNode
  "Takes JSON as a string or as EDN and returns a com.fasterxml.jackson.databind.JsonNode. Throws
  an exception if the provided JSON is not valid JSON."
  [json-string]
  (try
    (JsonLoader/fromString json-string)
    (catch JsonParseException e
      (warn "Invalid JSON when trying to validate" json-string e)
      (errors/throw-service-error :bad-request (str "Invalid JSON: " (.getMessage e))))))

(defn parse-json-schema
  "Convert a JSON string into a com.github.fge.jsonschema.main.JsonSchema object."
  [json-string]
  (->> json-string
       JsonLoader/fromString
       (.getJsonSchema (JsonSchemaFactory/byDefault))))

(defn validate-json
  "Performs schema validation using the provided JSON schema and the given json string to validate.
  Uses com.github.fge.jsonschema to perform the validation. The JSON schema must be provided as
  a com.github.fge.jsonschema.main.JsonSchema object and the json-to-validate must be a string."
  [json-schema json-to-validate]
  (let [validation-report (.validate json-schema (json-string->JsonNode json-to-validate))
        errors (parse-validation-report validation-report)]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))))

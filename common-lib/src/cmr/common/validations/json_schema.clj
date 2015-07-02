(ns cmr.common.validations.json-schema
  "Functions used to perform JSON schema validation. See http://json-schema.org/ for more details."
  (:require [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common.log :as log :refer (warn)])
  (:import com.github.fge.jsonschema.main.JsonSchemaFactory
           com.github.fge.jackson.JsonLoader))

(defn- parse-error-report
  "Parses the error-report to return a human friendly error message.

  Example:
  {:instance {:pointer \"/provider\"}
   :message \"object instance has properties which are not allowed by the schema: [\"123\"]\"
  ... other keys ignored}"
  [error-report]
  (str (get-in error-report [:instance :pointer]) " " (:message error-report)))

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
    (for [error-report (seq (.asJson report))]
      (let [json-error-report (json/decode (.toString error-report) true)]
        (conj (parse-nested-error-report (:reports json-error-report))
              (parse-error-report json-error-report))))))

(defn- json->JsonNode
  "Takes JSON as a string or as EDN and returns a com.fasterxml.jackson.databind.JsonNode. Throws
  an exception if the provided JSON is not valid JSON."
  [json]
  (try
    (let [json-string (if (string? json)
                        json
                        (json/generate-string json))]
      (JsonLoader/fromString json-string))
    (catch Exception e
      (warn "Invalid JSON when trying to validate" json e)
      (errors/throw-service-error :bad-request (str "Invalid JSON: " (.getMessage e))))))

(defn validate-against-json-schema
  "Performs schema validation using the provided JSON schema and the given json string to validate.
  Uses com.github.fge.jsonschema to perform the validation.

  Note that the provided schema is expected to always be valid. If the schema is invalid an
  exception will be raised. The intent of this function is only to validate the incoming JSON
  against the schema."
  [json-schema-str json-to-validate]
  (let [json-schema (->> json-schema-str
                         JsonLoader/fromString
                         (.getJsonSchema (JsonSchemaFactory/byDefault)))
        json-node-to-validate (json->JsonNode json-to-validate)
        validation-report (.validate json-schema json-node-to-validate)]
    (parse-validation-report validation-report)))

(def validations-to-perform
  "A set containing all of the validations to run."
  #{validate-against-json-schema})

(defn perform-validations
  "Runs all of the JSON schema validations gathering a list of errors from the validations. Throws
  a bad request service error when any errors are returned by the validations."
  [json-schema-str json-to-validate]
  (let [errors (mapcat #(% json-schema-str json-to-validate) validations-to-perform)]
    (when (seq errors)
      (errors/throw-service-errors :bad-request errors))))

(comment
  ;; Sample nested error report to pass into parse-nested-error-report
  ;; {:/properties/provider/oneOf/0
  ;;  [{:level "error"
  ;;    :schema {:loadingURI "#" :pointer "/properties/provider/oneOf/0"}
  ;;    :instance {:pointer "/provider"}
  ;;    :domain "validation"
  ;;    :keyword "type"
  ;;    :message "instance type (object) does not match any allowed primitive type (allowed: ["string"])"
  ;;    :found "object"
  ;;    :expected ["string"]}]
  ;;  :/properties/provider/oneOf/1
  ;;  [{:level "error"
  ;;    :schema {:loadingURI "#" :pointer "/definitions/valueOptionMap"}
  ;;    :instance {:pointer "/provider"}
  ;;    :domain "validation"
  ;;    :keyword "additionalProperties"
  ;;    :message "object instance has properties which are not allowed by the schema: ["123"]"
  ;;    :unwanted ["123"]}]}
  )

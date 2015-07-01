(ns cmr.common.validations.json-schema
  "Functions used to perform JSON schema validation."
  (:require [cheshire.core :as json]
            [cmr.common.services.errors :as err])
  (:import com.github.fge.jsonschema.main.JsonSchemaFactory
           com.github.fge.jackson.JsonLoader))

(defn- multiple-potential-types-validation
  "Parses error responses which have a field that could have multiple potential types. For example
  some values may accept a string or a map."
  [error-map]
  (when-let [report-keys (keys (:reports error-map))]
    (let [one-of-errors (filter #(re-matches #".*oneOf.*" (name %)) report-keys)]
      (mapcat (fn [one-of-error-key] (for [sub-error (one-of-error-key (:reports error-map))]
                                       (str (:pointer (:instance sub-error)) " "
                                            (:message sub-error))))
              one-of-errors))))

(defn- parse-validation-report
  "Takes a validation report and returns a sequence of any errors contained in the report. Returns
  nil if there are no errors. Takes a com.github.fge.jsonschema.core.report.ListProcessingReport.
  See http://fge.github.io/json-schema-validator/2.2.x/index.html for details."
  [report]
  (when-let [error-reports (seq (.asJson report))]
    (flatten (map (fn [error-report]
                    (let [json-error (json/decode (.toString error-report) true)]
                      (conj (multiple-potential-types-validation json-error)
                            (str (:pointer (:instance json-error)) " " (:message json-error)))))
                  error-reports))))

(defn validate-against-json-schema
  "Performs schema validation using the provided JSON schema and the given json string to validate.
  Uses com.github.fge.jsonschema to perform the validation. Throws a bad request service error when
  validation fails."
  [json-schema-str json-to-validate-str]
  (let [json-schema (->> json-schema-str
                         JsonLoader/fromString
                         (.getJsonSchema (JsonSchemaFactory/byDefault)))
        json-to-validate (JsonLoader/fromString (json/generate-string json-to-validate-str))
        validation-report (.validate json-schema json-to-validate)
        errors (parse-validation-report validation-report)]
    (when (seq errors)
      (err/throw-service-errors :bad-request errors))))

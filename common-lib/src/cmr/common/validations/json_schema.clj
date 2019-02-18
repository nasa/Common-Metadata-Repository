(ns cmr.common.validations.json-schema
  "Functions used to perform JSON schema validation. See http://json-schema.org/
  for more details."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [cmr.common.log :as log :refer [warn info]]
   [cmr.common.services.errors :as errors]
   [cmr.schema-validation.json-schema :as json-schema])
  (:import
   (org.everit.json.schema ValidationException)
   (org.json JSONException)))

(defn parse-json-schema-from-path
  "Convenience function for not needing to require extra dependencies throughout CMR."
  ([path]
   (json-schema/parse-json-schema-from-path path))
  ([path uri]
   (json-schema/parse-json-schema-from-path path uri)))

(defn parse-json-schema
  "Convenience function for not needing to require extra dependencies throughout CMR."
  [schema-def]
  (json-schema/parse-json-schema schema-def))

(defn json-string->json-schema
  "Convenience function for not needing to require extra dependencies throughout CMR."
  [schema-string]
  (json-schema/json-string->json-schema schema-string))

(defn validate-json
  "Performs schema validation using the provided JSON schema and the given
  json string to validate."
  [json-schema json-to-validate]
  (try
    (json-schema/validate-json json-schema json-to-validate true)
    (catch JSONException e
      (errors/throw-service-error :bad-request (str "Invalid JSON: " (.getMessage e))))
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

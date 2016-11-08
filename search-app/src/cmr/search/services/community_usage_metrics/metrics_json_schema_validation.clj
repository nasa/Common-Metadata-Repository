(ns cmr.search.services.community-usage-metrics.metrics-json-schema-validation
  "This contains JSON schema validations related to the community usage metrics"
  (:require
   [cmr.common.validations.json-schema :as js]))

(def ^:private community-usage-metrics-schema-structure
  "Schema for community usage metrics as json."
  {:type :array
   :items {:type :object
           :additionalProperties false
           :properties {:short-name {:type :string :minLength 1 :maxLength 85}
                        :version {:type :string :maxLength 20}
                        :access-count {:type :integer}}
           :required [:short-name :access-count]}})

(def ^:private metrics-schema
  "The JSON schema used to validate humanizer requests"
  (js/parse-json-schema community-usage-metrics-schema-structure))

(defn validate-metrics-json
  "Validates the community usage metrics JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! metrics-schema json-str))

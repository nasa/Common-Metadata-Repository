(ns cmr.search.services.humanizer.humanizer-json-schema-validation
  "This contains JSON schema validations related to the humanizer"
  (:require [cmr.common.validations.json-schema :as js]
            [cmr.search.services.tagging.json-schema-validation :as jv]))

(def ^:private humanizer-schema-structure
  "Schema for humanizer as json."
  {:type :array
   :items {:type :object
           :additionalProperties false
           :properties {:type {:type :string :minLength 1 :maxLength 255}
                        :field {:type :string :minLength 1 :maxLength 255}
                        :source_value {:type :string :minLength 1 :maxLength 255}
                        :replacement_value {:type :string :minLength 1 :maxLength 255}
                        :reportable {:type :boolean}
                        :order {:type :integer}
                        :priority {:type :integer}}
           :required [:type :field]}})

(def ^:private humanizer-schema
  "The JSON schema used to validate humanizer requests"
  (js/parse-json-schema humanizer-schema-structure))

(defn validate-humanizer-json
  "Validates the humanizer JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (jv/validate-json humanizer-schema json-str))


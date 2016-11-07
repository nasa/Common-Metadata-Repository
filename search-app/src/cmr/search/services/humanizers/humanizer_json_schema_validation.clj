(ns cmr.search.services.humanizers.humanizer-json-schema-validation
  "This contains JSON schema validations related to the humanizer"
  (:require [cmr.common.validations.json-schema :as js]))

(def ^:private humanizer-schema-structure
  "Schema for humanizer as json."
  {:type :object
   :properties {:humanizers  {:type :array
                              :items {:type :object
                                       :additionalProperties false
                                       :properties {:type {:type :string :minLength 1 :maxLength 255}
                                                    :field {:type :string :minLength 1 :maxLength 255}
                                                    :source_value {:type :string :minLength 1 :maxLength 255}
                                                    :replacement_value {:type :string :minLength 1 :maxLength 255}
                                                    :reportable {:type :boolean}
                                                    :order {:type :integer}
                                                    :priority {:type :integer}}
                                       :required [:type :field]}
                               :minItems 1}
                :community-usage-metrics {:type :array
                                          :items {:type :object
                                                   :additionalProperties false
                                                   :properties {:short-name {:type :string :minLength 1 :maxLength 85}
                                                                :version {:type :string :maxLength 20}
                                                                :access-count {:type :integer}}
                                                   :required [:short-name :access-count]}}}})

(def ^:private humanizer-schema
  "The JSON schema used to validate humanizer requests"
  (js/parse-json-schema humanizer-schema-structure))

(defn validate-humanizer-json
  "Validates the humanizer JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! humanizer-schema json-str))

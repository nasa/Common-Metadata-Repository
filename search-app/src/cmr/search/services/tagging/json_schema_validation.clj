(ns cmr.search.services.tagging.json-schema-validation
  "This contains JSON schema validations related to the tagging service"
  (:require [cmr.common.validations.json-schema :as js]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]))

(def ^:private base-tag-schema-structure
  "Base Schema for tags as json."
  {:type :object
   :additionalProperties false
   :properties {:tag-key {:type :string :minLength 1 :maxLength 1030}
                :description {:type :string :minLength 1 :maxLength 4000}}
   :required [:tag-key]})

(def ^:private create-tag-schema
  "The JSON schema used to validate tag creation requests"
  (js/parse-json-schema base-tag-schema-structure))

(def ^:private update-tag-schema
  "The JSON schema used to update update tag requests. Update requests are allowed to specify the
  originator id. They can't change it but it's allowed to be passed in because the tag fetch response
  will include it."
  (js/parse-json-schema (assoc-in base-tag-schema-structure [:properties :originator-id]
                                  {:type :string})))

(def maximum-data-length
  "The maximum length of data in bytes that can be passed to tag association"
  32768)

(def ^:private collections-tagging-schema-structure
  "Schema for tagging collections as json."
  {:type :array
   :items {:type :object
           :additionalProperties false
           :properties {:concept-id {:type :string :minLength 1 :maxLength 255}
                        :revision-id {:type :integer}
                        :data {:anyOf [{:type :string :minLength 1 :maxLength maximum-data-length}
                                       {:type :boolean}
                                       {:type :integer}
                                       {:type :number}
                                       {:type :array}
                                       {:type :object}]}}
           :required [:concept-id]}})

(def ^:private collections-tagging-schema
  "The JSON schema used to validate tag association by collections requests"
  (js/parse-json-schema collections-tagging-schema-structure))

(defn- validate-json
  "Validates the JSON string against the given schema. Throws a service error if it is invalid."
  [schema json-str]
  (when-let [errors (seq (js/validate-json schema json-str))]
    (errors/throw-service-errors :bad-request errors)))

(defn validate-create-tag-json
  "Validates the create tag JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (validate-json create-tag-schema json-str))

(defn validate-update-tag-json
  "Validates the update tag JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (validate-json update-tag-schema json-str))

(defn validate-collections-json
  "Validates the collections JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (validate-json collections-tagging-schema json-str))


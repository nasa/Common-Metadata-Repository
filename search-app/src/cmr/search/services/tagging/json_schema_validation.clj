(ns cmr.search.services.tagging.json-schema-validation
  "This contains JSON schema validations related to the tagging service"
  (:require [cmr.common.validations.json-schema :as js]
            [cheshire.core :as json]
            [cmr.common.services.errors :as errors]))

(def ^:private base-tag-schema-structure
  "Base Schema for tags as json."
  {:type :object
   :additionalProperties false
   :properties {:tag_key {:type :string :minLength 1 :maxLength 1030}
                :description {:type :string :minLength 1 :maxLength 4000}}
   :required [:tag_key]})

(def ^:private create-tag-schema
  "The JSON schema used to validate tag creation requests"
  (js/parse-json-schema base-tag-schema-structure))

(def ^:private update-tag-schema
  "The JSON schema used to update update tag requests. Update requests are allowed to specify the
  originator id. They can't change it but it's allowed to be passed in because the tag fetch response
  will include it."
  (js/parse-json-schema (assoc-in base-tag-schema-structure [:properties :originator_id]
                                  {:type :string})))

(def maximum-data-length
  "The maximum length of data in bytes that can be passed to tag association"
  32768)

(def ^:private collections-tagging-schema-structure
  "Schema for tagging collections as json."
  {:type :array
   :items {:type :object
           :additionalProperties false
           :properties {:concept_id {:type :string :minLength 1 :maxLength 255}
                        :revision_id {:type :integer}
                        :data {:anyOf [{:type :string :minLength 1 :maxLength maximum-data-length}
                                       {:type :boolean}
                                       {:type :integer}
                                       {:type :number}
                                       {:type :array}
                                       {:type :object}]}}
           :required [:concept_id]}})

(def ^:private collections-tagging-schema
  "The JSON schema used to validate tag association by collections requests"
  (js/parse-json-schema collections-tagging-schema-structure))

(defn validate-json-structure
  "Validates the given JSON string is a valid json structural wise. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json!
    (js/parse-json-schema {:anyOf [{:type :string}
                                   {:type :boolean}
                                   {:type :integer}
                                   {:type :number}
                                   {:type :array}
                                   {:type :object}]})
    json-str))

(defn validate-create-tag-json
  "Validates the create tag JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! create-tag-schema json-str))

(defn validate-update-tag-json
  "Validates the update tag JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! update-tag-schema json-str))

(defn validate-tag-associations-json
  "Validates the tag associations JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! collections-tagging-schema json-str))


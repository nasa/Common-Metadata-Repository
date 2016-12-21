(ns cmr.access-control.data.group-schema
  (:require [cmr.common.validations.json-schema :as js]))

(def ^:private group-schema-structure
  "Schema for groups as json."
  {:type :object
   :additionalProperties false
   :properties {:name {:type :string :minLength 1 :maxLength 100}
                :provider_id {:type :string :minLength 1 :maxLength 50}
                :description {:type :string :minLength 1 :maxLength 255}
                :legacy_guid {:type :string :minLength 1 :maxLength 50}
                :members {:type :array :items {:type :string :minLength 1 :maxLength 100}}}
   :required [:name :description]})

(def ^:private group-schema
  "The JSON schema used to validate groups"
  (js/parse-json-schema group-schema-structure))

(def ^:private group-members-schema-structure
  "Schema defining list of usernames sent to add or remove members in a group"
  {:type :array :items {:type :string :minLength 1 :maxLength 50}})

(def ^:private group-members-schema
  "The JSON schema used to validate a list of group members"
  (js/parse-json-schema group-members-schema-structure))

(defn validate-group-json
  "Validates the group JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! group-schema json-str))

(defn validate-group-members-json
  "Validates the group mebers JSON string against the schema. Throws a service error if it is invalid."
  [json-str]
  (js/validate-json! group-members-schema json-str))

(ns cmr.search.services.tagging.tag-validation
  "This contains functions for validating the business rules of tag."
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [cmr.common.util :as util]
            [cmr.common.services.errors :as errors]
            [cmr.common.validations.core :as v]
            [cmr.search.services.tagging.json-schema-validation :as jv]))

(defn- validate-tag-key
  "Validates there is no / character in tag-key, throws service error if there is."
  [tag-key]
  (when (re-find #"/" tag-key)
    (errors/throw-service-error
      :invalid-data
      (format "Tag key [%s] contains '/' character. Tag keys cannot contain this character."
              tag-key))))

(defn- tag-json->tag
  "Returns the tag in JSON from the given request body with the tag-key converted to lowercase."
  [tag-json-str]
  (-> (json/parse-string tag-json-str true)
      util/map-keys->kebab-case
      ;; tag-key is always in lowercase
      (update :tag-key str/lower-case)))

(defn sanitized-json
  "Returns the json string with deashes in field names replaced by underscores,
  e.g. tag-key will be changed to tag_key."
  [json-str]
  (jv/validate-json-structure json-str)
  (try
    (let [parsed (json/parse-string json-str true)
          parsed (if (sequential? parsed)
                   (map util/map-keys->snake_case parsed)
                   (util/map-keys->snake_case parsed))]
      (json/generate-string parsed))
    (catch Exception _
      ;; If the json-str contains data that cannot be handled by the map-keys->snake_case function,
      ;; exception will be thrown.
      ;; We just return json-str in this case, as the error will be caught later on.
      json-str)))

(defn create-tag-json->tag
  "Validates the create tag json and returns the parsed tag"
  [tag-json-str]
  (jv/validate-create-tag-json (sanitized-json tag-json-str))
  (let [tag (tag-json->tag tag-json-str)]
    (validate-tag-key (:tag-key tag))
    tag))

(defn update-tag-json->tag
  "Validates the update tag json and returns the parsed tag"
  [tag-json-str]
  (jv/validate-update-tag-json (sanitized-json tag-json-str))
  (tag-json->tag tag-json-str))

(def ^:private update-tag-validations
  "Service level validations when updating a tag."
  [(v/field-cannot-be-changed :tag-key)
   ;; Originator id cannot change but we allow it if they don't specify a value.
   (v/field-cannot-be-changed :originator-id true)])

(defn validate-update-tag
  "Validates a tag update."
  [existing-tag updated-tag]
  (v/validate! update-tag-validations (assoc updated-tag :existing existing-tag)))


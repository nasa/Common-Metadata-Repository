(ns cmr.search.services.tagging.tagging-service-messages
  "This contains error response messages for the tagging service"
  (:require [clojure.string :as str]
            [cmr.common.util :as util]))

(def token-required-for-tag-modification
  "Tags cannot be modified without a valid user token.")

(defn tag-already-exists
  [tag concept-id]
  (format "A tag with tag-key [%s] already exists with concept id [%s]." (:tag-key tag) concept-id))

(def field-may-not-contain-separator
  "Validation format message so %s is included for the field"
  "%s may not contain the Group Separator character. ASCII decimal value: 29 Unicode: U+001D")

(defn tag-does-not-exist
  [tag-key]
  (format "Tag could not be found with tag-key [%s]" tag-key))

(defn bad-tag-concept-id
  [concept-id]
  (format "[%s] is not a valid tag concept id." concept-id))

(defn tag-deleted
  [tag-key]
  (format "Tag with tag-key [%s] was deleted." tag-key))

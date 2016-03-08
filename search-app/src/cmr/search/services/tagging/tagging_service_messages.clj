(ns cmr.search.services.tagging.tagging-service-messages
  "This contains error response messages for the tagging service"
  (:require [clojure.string :as str]))

(def token-required-for-tag-modification
  "Tags cannot be modified without a valid user token.")

(defn tag-already-exists
  [tag concept-id]
  (format "A tag with tag-key [%s] already exists with concept id [%s]." (:tag-key tag) concept-id))

(def field-may-not-contain-separator
  "Validation format message so %s is included for the field"
  "%s may not contain the Group Separator character. ASCII decimal value: 29 Unicode: U+001D")

(defn tag-does-not-exist
  [concept-id]
  (format "Tag could not be found with concept id [%s]" concept-id))

(defn bad-tag-concept-id
  [concept-id]
  (format "[%s] is not a valid tag concept id." concept-id))

(defn tag-deleted
  [concept-id]
  (format "Tag with concept id [%s] was deleted." concept-id))

(defn inaccessible-collections
  [concept-ids]
  (format "The following collections do not exist or are not accessible: %s."
          (str/join ", " concept-ids)))
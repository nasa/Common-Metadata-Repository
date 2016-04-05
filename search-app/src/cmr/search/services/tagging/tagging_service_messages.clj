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

(defn no-collections
  []
  "At least one collection must be provided for tag association.")

(defn conflict-collections
  [concept-ids]
  (format "Unable to tag a collection revision and the whole collection at the same time for the following collections: %s."
          (str/join ", " concept-ids)))

(defn inaccessible-collections
  [concept-ids]
  (format "The following collections do not exist or are not accessible: %s."
          (str/join ", " concept-ids)))

(defn- coll->message
  "Returns the message representation of the given collection"
  [{:keys [concept-id revision-id]}]
  (format "{concept-id %s, revision-id %s}" concept-id revision-id))

(defn inaccessible-collection-revisions
  [colls]
  (format "The following collection revisions do not exist or are not accessible: %s."
          (str/join ", " (map coll->message colls))))

(defn tombstone-collections
  [colls]
  (format "The following collections are tombstones which are not allowed for tag association: %s."
          (str/join ", " (map coll->message colls))))

(defn collections-data-too-long
  [concept-ids]
  (format "The following collections tag association data exceed the maximum length of 32KB: %s."
          (str/join ", " concept-ids)))
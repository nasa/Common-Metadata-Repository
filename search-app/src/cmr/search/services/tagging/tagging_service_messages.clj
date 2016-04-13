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

(defn no-tag-associations
  []
  "At least one collection must be provided for tag association.")

(defn conflict-tag-associations
  [concept-ids]
  (format "Unable to tag a collection revision and the whole collection at the same time for the following collections: %s."
          (str/join ", " concept-ids)))

(defn inaccessible-collection
  [concept-id]
  (format "Collection [%s] does not exist or is not visible." concept-id))

(defn inaccessible-collection-revision
  [{:keys [concept-id revision-id]}]
  (format "Collection with concept id [%s] revision id [%s] does not exist or is not visible."
          concept-id revision-id))

(defn tombstone-collection
  [{:keys [concept-id revision-id]}]
  (format (str "Collection with concept id [%s] revision id [%s] is a tombstone. "
               "We don't allow tagging individual revisions that are tombstones.")
          concept-id revision-id))

(defn tag-association-data-too-long
  [{:keys [concept-id revision-id]}]
  (format "Tag association data exceed the maximum length of 32KB for collection with concept id [%s] revision id [%s]."
          concept-id revision-id))

(defn delete-tag-association-not-found
  [native-id]
  (let [[tag-key concept-id revision-id] (str/split native-id #"/")]
    (if revision-id
      (format (str "Tag [%s] is not associated with the specific collection concept revision "
                   "concept id [%s] and revision id [%s].")
              tag-key concept-id revision-id)
      (format "Tag [%s] is not associated with collection [%s]." tag-key concept-id))))



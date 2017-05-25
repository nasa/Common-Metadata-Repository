(ns cmr.search.services.messages.association-messages
  "Contains messages for validating tag association and variable associations"
  (require
   [clojure.string :as string]))

(defn no-associations
  "Returns the error message when no collection is provided for association"
  [assoc-type]
  (format "At least one collection must be provided for %s association."
          (name assoc-type)))

(defn conflict-associations
  [assoc-type concept-ids]
  (format (str "Unable to create %s association on a collection revision and the whole collection "
               "at the same time for the following collections: %s.")
          (name assoc-type)
          (string/join ", " concept-ids)))

(defn tombstone-collection
  [assoc-type {:keys [concept-id revision-id]}]
  (format (str "Collection with concept id [%s] revision id [%s] is a tombstone. We don't allow "
               "%s association with individual collection revisions that are tombstones.")
          concept-id revision-id (name assoc-type)))

(defn association-data-too-long
  [assoc-type {:keys [concept-id revision-id]}]
  (format (str "%s association data exceed the maximum length of 32KB for collection "
               "with concept id [%s] revision id [%s].")
          (string/capitalize (name assoc-type))
          concept-id revision-id))

(defn inaccessible-collection
  [concept-id]
  (format "Collection [%s] does not exist or is not visible." concept-id))

(defn inaccessible-collection-revision
  [{:keys [concept-id revision-id]}]
  (format "Collection with concept id [%s] revision id [%s] does not exist or is not visible."
          concept-id revision-id))

(defn delete-association-not-found
  [assoc-type native-id]
  (let [[identifier concept-id revision-id] (string/split native-id #"/")]
    (if revision-id
      (format (str "%s [%s] is not associated with the specific collection concept revision "
                   "concept id [%s] and revision id [%s].")
              (string/capitalize (name assoc-type)) identifier concept-id revision-id)
      (format "%s [%s] is not associated with collection [%s]."
              (string/capitalize (name assoc-type)) identifier concept-id))))

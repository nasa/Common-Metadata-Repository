(ns cmr.search.services.messages.association-messages
  "Contains messages for validating tag association and variable associations"
  (:require
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

(defn conflict-generic-associations
  [assoc-type concept-ids]
  (format (str "Unable to create %s association on a concept revision and the whole concept "
               "at the same time for the following concepts: %s.")
          (name assoc-type)
          (string/join ", " concept-ids)))

(defn same-concept-generic-association
  [concept-id]
  (format (str "The concept [%s] can not be associated with itself in a generic association.")
          concept-id)) 

(defn non-supported-generic-association-types
  [non-supported-types]
  (format (str "The following concept types [%s] are not supported for generic associations.")
          non-supported-types))

(defn cannot-assoc-msg
  [concept-id assoc-concept-ids]
  (format (str "The following concept ids [%s] can not be associated with concept id [%s] "
               "because collection/[service|tool|variable] associations are not supported "
               "by the new generic association api.")
          assoc-concept-ids concept-id))
 
(defn tombstone-collection
  [assoc-type {:keys [concept-id revision-id]}]
  (format (str "Collection with concept id [%s] revision id [%s] is a tombstone. We don't allow "
               "%s association with individual collection revisions that are tombstones.")
          concept-id revision-id (name assoc-type)))

(defn tombstone-concept
  [assoc-type {:keys [concept-id revision-id]}]
  (format (str "Concept with concept id [%s] revision id [%s] is a tombstone. We don't allow "
               "%s association with individual concept revisions that are tombstones.")
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

(defn inaccessible-concept
  [concept-id]
  (format "Concept [%s] does not exist or is not visible." concept-id))

(defn inaccessible-collection-revision
  [{:keys [concept-id revision-id]}]
  (format "Collection with concept id [%s] revision id [%s] does not exist or is not visible."
          concept-id revision-id))

(defn inaccessible-concept-revision
  [{:keys [concept-id revision-id]}]
  (format "Concept with concept id [%s] revision id [%s] does not exist or is not visible."
          concept-id revision-id))

(defn no-permission-collection-assoc
  [concept-id]
  (format "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [%s] to make the association." concept-id))

(defn no-permission-collection-dissoc
  [concept-id]
  (format "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of collection [%s] or provider of service/tool to delete the association." concept-id))

(defn no-permission-concept-assoc
  [concept-id]
  (format "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of concept [%s] to make the association." concept-id))

(defn no-permission-concept-dissoc
  [concept-id]
  (format "User doesn't have update permission on INGEST_MANAGEMENT_ACL for provider of concept [%s] or provider of the concepts associated with it to delete the association." concept-id))

(defn delete-association-not-found
  [assoc-type native-id]
  (let [[identifier concept-id revision-id] (string/split native-id #"/")]
    (if revision-id
      (format (str "%s [%s] is not associated with the specific collection concept revision "
                   "concept id [%s] and revision id [%s].")
              (string/capitalize (name assoc-type)) identifier concept-id revision-id)
      (format "%s [%s] is not associated with collection [%s]."
              (string/capitalize (name assoc-type)) identifier concept-id))))

(defn delete-generic-association-not-found
  [native-id]
  (format (str "Generic association with native-id [%s] is not found.") native-id))

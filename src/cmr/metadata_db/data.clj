(ns cmr.metadata-db.data
  "Defines a protocol for CRUD operations on concepts.")

(defmulti get-concept-id
  "Return a distinct identifier for the given arguments."
  (fn [db concept-type provider-id native-id]
    concept-type))

(defmulti generate-concept-id
  "Create a concept-id for a given concept type and provider id."
  (fn [db concept]
    (:concept-type concept)))

(defmulti get-concept
  "Gets a version of a concept with a given concept-id and revision-id. If the
  revision-id is not given or is nil then the latest revision is returned."
  (fn [db concept-type & args]
    concept-type))

(defmulti get-concept-by-provider-id-native-id-concept-type
  "Gets a version of a concept that has the same concept-type, provider-id, and native-id
  as the given concept."
  (fn [db concept]
    (:concept-type concept)))

(defmulti get-concepts
  "Get a sequence of concepts by specifying a list of
  tuples holding concept-id/revision-id"
  (fn [db concept-type concept-id-revision-id-tuples]
    concept-type))

(defmulti save-concept
  "Saves a concept and returns the revision id. If the concept already
  exists then a new revision will be created. If a revision-id is
  included and it is not valid, e.g. the revision already exists,
  then an exception is thrown."
  (fn [db concept]
    (:concept-type concept)))

(defmulti force-delete
  "Remove a revision of a concept from the database completely."
  (fn [db concept-type concept-id revision-id]
    concept-type))

(defmulti reset-concepts
  "Delete all concepts from the database.  USE WITH CAUTION."
  (fn [db concept-type]
    concept-type))
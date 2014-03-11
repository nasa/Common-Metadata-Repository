(ns cmr.metadata-db.data)
  
(defprotocol ConceptStore
  "Functions for saving and retrieving concepts"

  (get-concept
    [db concept-id]
    "Gets the vector of versions of a concept with a given id")

  (insert-concept
    [db concept]
    "Inserts a concept. If the concept already exists then a new version will 
    be created. The version is returned.")

  (delete-concept
    [db concept-type provider-id id]
    "Marks a concept for deletion (tombstone) by id."))
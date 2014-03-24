(ns cmr.metadata-db.data
  "Defines a protocol for CRUD operations on concepts.")
  
(defprotocol ConceptStore
  "Functions for saving and retrieving concepts"
  (get-concept-id
    [db concept-type provider-id native-id]
    "Return a distinct identifier for the given arguments.")

  (get-concept
    [db concept-id revision-id]
    "Gets a version of a concept with a given id")
  
  (get-concepts
    [db concept-id-revision-id-tuples]
    "Get a sequence of concepts by specifying a list of
    tuples holding concept-id/revision-id") 
    
  (save-concept
    [db concept]
    "Saves a concept and returns the revision id. If the concept already 
    exists then a new revision will be created. If a revision-id is 
    included and it is not valid, e.g. the revision already exists, 
    then an exception is thrown.")
  
  (delete-concept
    [db concept-id]
    "Marks a concept as deleted by saving a tombstone record.  The tombstone
    is given a revision id one higher than the highest revision id already
    present. If the concept does not already exist then an exception is 
    thrown resulting in a 404 status code.")
  
  (force-delete
    [db concept-id revision-id]
    "Remove a revision of a concept from the database completely.")
  
  (reset
    [db]
    "Delete all concepts from the database.  USE WITH CAUTION."))
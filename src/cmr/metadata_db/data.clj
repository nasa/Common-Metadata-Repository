(ns cmr.metadata-db.data)
  
(defprotocol ConceptStore
  "Functions for saving and retrieving concepts"
  (get-concept-id
    [db concept-type provider-id native-id]
    "Return a distinct identifier for the given arguments.")

  (get-concept
    [db concept-id, revision-id]
    "Gets a version of a concept with a given id")
  
  (get-concepts
    [db concept-id-revision-id-tuples]
    "Get multiple concepts at once by specifying a list vector-of
    tuples holding concept-id/revision-id") 
    
  (save-concept
    [db concept]
    "Inserts a concept. If the concept already exists then a new revision will 
    be created. The revision is returned. If a revision-id is
    included in the concept and this revision already exists, an 
    exception is thrown."))
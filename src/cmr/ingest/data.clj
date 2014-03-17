(ns cmr.ingest.data
  "Defines a protocol to store concepts in metadata db and indexer.")
  
(defprotocol ConceptStore
  "Functions for saving and retrieving concepts"
  (get-concept-id
    [db concept-type provider-id native-id]
    "Return a distinct identifier for the given arguments.")
    
  (save-concept
    [db concept]
    "Saves a concept and returns the revision id. If the concept already 
    exists then a new revision will be created. If a revision-id is 
    included and it is not valid, e.g. the revision already exists, 
    then an exception is thrown."))

(defprotocol ConceptIndexStore
  "Functions for staging concepts for the purposes of indexing."
    
  (stage-concept-for-indexing
    [db concept-id revision-id]
    "Stage attributes of a concept for indexer app consumption."))
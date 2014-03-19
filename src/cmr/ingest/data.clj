(ns cmr.ingest.data
  "Defines a protocol to store concepts in metadata db and indexer.")

(defprotocol ConceptStore
  "Functions for saving and retrieving concepts"
  (get-concept-id
    [db concept-type provider-id native-id]
    "Return a distinct identifier for the given arguments.")
  
  (save-concept
    [db concept]
    "Saves a concept in metadata db and index.")

  (delete-concept
    [db concept]
    "Delete a concept from metatdata db."))

(defprotocol ConceptIndexStore
  "Functions for staging concepts for the purposes of indexing."
  
  (index-concept
    [db concept-id revision-id]
    "Forward newly created concept for indexer app consumption.")
  
  (delete-concept-from-index
    [db concept-id revision-id]
    "Delete a concept with given revision-id from index."))
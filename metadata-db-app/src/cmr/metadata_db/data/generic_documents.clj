(ns cmr.metadata-db.data.generic-documents
  "Defines a protocol for CRUD operations on generic documents.")

(defprotocol GenericDocsStore
  "Functions for saving and retrieving generic documents"

  (generate-concept-id [db document] "")

  (save-concept
    [db provider concept]
    "Saves a document and returns the document id. If the document already
    exists then an exception is thrown.")

  (get-concept
   [db concept-type provider concept-id]
   [db concept-type provider concept-id revision-id]
   "Get a sequence of all the documents.")

  (get-concepts
    [db concept-type provider concept-id-revision-id-tuples]
    "Get the document with given id.")

  (get-latest-concepts
   [db concept-type provider concept-ids]
   "Get the most recent documents")

  (update-document
    [db provider concept]
    "Updates an existing document in the database based on the
    document map's document-id value.")

  ;; CMR-8181
  ;; TO-DO -- not sure about 'document instances' language below
  (force-delete
    [db concept-type provider concept-id revision-id]
    "Remove a document from the database completely, including all of its
   document instances.")

  ;; CMR-8181
  ;; TO-DO -- not sure about 'document instances' language below
  (reset
    [db]
    "Delete all documents from the database including their document instances. 
    USE WITH CAUTION."))

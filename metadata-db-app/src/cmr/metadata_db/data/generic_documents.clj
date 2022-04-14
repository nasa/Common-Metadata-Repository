(ns cmr.metadata-db.data.generic-documents
  "Defines a protocol for CRUD operations on generic documents.")

(defprotocol GenericDocsStore
  "Functions for saving and retrieving generic documents"
  
  (save-document
   [db document]
   "Saves a document and returns the document id. If the document already
    exists then an exception is thrown.")
  
  (get-documents
   [db]
   "Get a sequence of all the documents.")
  
  (get-document
   [db document-id]
   "Get the document with given id.")
  
  (update-document
   [db document]
   "Updates an existing document in the database based on the
    document map's document-id value.")
  
  ;; CMR-8181
  ;; TO-DO -- not sure about 'document instances' language below
  (delete-document
   [db document]
   "Remove a document from the database completely, including all of its
   document instances.")
  
  ;; CMR-8181
  ;; TO-DO -- not sure about 'document instances' language below
  (reset-documents
   [db]
   "Delete all documents from the database including their document instances. 
    USE WITH CAUTION."))
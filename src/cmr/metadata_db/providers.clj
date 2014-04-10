(ns cmr.metadata-db.providers
  "Defines a protocol for CRUD operations on providers")
  
(defprotocol ProviderStore
  "Functions for saving, retrieving, deleting providers."
  
  (save-provider
    [db provider-id]
    "Saves a provider and returns the provider id. If the provider already 
    exists then an exception is thrown.")

  (get-providers
    [db]
    "Get a sequence of all the providers.") 
  
  (delete-provider
    [db provider-id]
    "Remove a provider from the database completely, including all of its concepts.")
  
  (reset-providers
    [db]
    "Delete all providers from the database.  USE WITH CAUTION."))
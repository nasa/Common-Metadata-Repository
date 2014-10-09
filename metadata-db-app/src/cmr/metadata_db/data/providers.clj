(ns cmr.metadata-db.data.providers
  "Defines a protocol for CRUD operations on providers.")

(defprotocol ProvidersStore
  "Functions for saving and retrieving providers"

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
    "Delete all providers from the database including their concept tables.  USE WITH CAUTION."))


(ns cmr.bootstrap.embedded-system-helper
  "Contains general helper functions"
  (:require
   [cmr.metadata-db.data.providers :as p]))

(defn get-metadata-db
  "Returns the embedded metadata-db from the given bootstrap system"
  [system]
  (get-in system [:embedded-systems :metadata-db]))

(defn get-metadata-db-db
  "Returns the db instance of the metadata-db from the given system"
  [system]
  (get-in system [:embedded-systems :metadata-db :db]))

(defn get-indexer
  "Returns the embedded indexer from the given bootstrap system"
  [system]
  (get-in system [:embedded-systems :indexer]))

(defn get-virtual-products
  [system]
  (get-in system [:embedded-systems :virtual-products]))

(defn get-provider
  "Returns the metadata db provider that matches the given provider id.

  This differs from the helper function in `cmr.bootstrap.data.bulk-index`
  in that it takes a system data structure instead of a request-context
  data-structure."
  [system provider-id]
  (-> system
      (get-metadata-db-db)
      (p/get-provider provider-id)))

(defn get-providers
  "Returns all metadata db providers."
  [system]
  (-> system
      (get-metadata-db-db)
      (p/get-providers)))
